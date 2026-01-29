/*
 * *****************************************************************************
 * Copyright (C) 2014-2025 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Maps transmission boundaries in a baseband recording using signal energy analysis.
 * Detects when RF signal is present (transmission in progress) vs. silence (no transmission).
 */
public class TransmissionMapper
{
    // Energy detection parameters (matching P25P1DecoderLSMv2)
    private static final float ENERGY_EMA_FACTOR = 0.002f;
    private static final float ENERGY_SILENCE_RATIO = 0.15f;
    private static final float PEAK_DECAY = 0.99999f;

    // Transmission detection thresholds
    private static final long MIN_TRANSMISSION_MS = 180;   // Minimum 1 LDU duration
    private static final long MIN_GAP_MS = 500;            // Gap required to split transmissions
    private static final long BUFFER_MS = 100;             // Buffer for slice extraction

    // Energy tracking state
    private float mEnergyAverage = 0f;
    private float mPeakEnergy = 0f;
    private long mSampleCount = 0;
    private double mSampleRate = 0;

    // Signal period tracking
    private Long mSignalStartSample = null;
    private float mPeriodPeakEnergy = 0f;
    private double mPeriodSumEnergy = 0;
    private int mPeriodSampleCount = 0;

    // Detected transmissions
    private final List<SignalPeriod> mSignalPeriods = new ArrayList<>();

    /**
     * Maps all transmissions in a baseband WAV file.
     *
     * @param basebandWav path to the baseband WAV file
     * @return list of detected transmissions
     */
    public List<Transmission> mapTransmissions(Path basebandWav) throws IOException
    {
        return mapTransmissions(basebandWav.toFile());
    }

    /**
     * Maps all transmissions in a baseband WAV file.
     *
     * @param basebandFile the baseband WAV file
     * @return list of detected transmissions
     */
    public List<Transmission> mapTransmissions(File basebandFile) throws IOException
    {
        // Reset state
        mEnergyAverage = 0f;
        mPeakEnergy = 0f;
        mSampleCount = 0;
        mSignalStartSample = null;
        mSignalPeriods.clear();

        try(ComplexWaveSource source = new ComplexWaveSource(basebandFile, false))
        {
            source.setListener(buffer -> {
                Iterator<ComplexSamples> it = buffer.iterator();
                while(it.hasNext())
                {
                    processSamples(it.next());
                }
            });
            source.start();
            mSampleRate = source.getSampleRate();

            // Process entire file
            try
            {
                while(true)
                {
                    source.next(2048, true);
                }
            }
            catch(Exception e)
            {
                // End of file
            }
        }

        // Close any open signal period
        if(mSignalStartSample != null)
        {
            closeSignalPeriod(mSampleCount, false); // incomplete at end of file
        }

        // Convert signal periods to transmissions
        return convertToTransmissions();
    }

    /**
     * Process a batch of I/Q samples for energy detection.
     */
    private void processSamples(ComplexSamples samples)
    {
        float[] i = samples.i();
        float[] q = samples.q();

        for(int idx = 0; idx < i.length; idx++)
        {
            float energy = (i[idx] * i[idx]) + (q[idx] * q[idx]);
            mEnergyAverage += (energy - mEnergyAverage) * ENERGY_EMA_FACTOR;

            if(mEnergyAverage > mPeakEnergy)
            {
                mPeakEnergy = mEnergyAverage;
            }
            else
            {
                mPeakEnergy *= PEAK_DECAY;
            }

            float silenceThreshold = mPeakEnergy * ENERGY_SILENCE_RATIO;
            boolean isSignal = mPeakEnergy > 0 && mEnergyAverage >= silenceThreshold;

            if(isSignal)
            {
                if(mSignalStartSample == null)
                {
                    // Start of new signal period
                    mSignalStartSample = mSampleCount;
                    mPeriodPeakEnergy = 0f;
                    mPeriodSumEnergy = 0;
                    mPeriodSampleCount = 0;
                }
                mPeriodPeakEnergy = Math.max(mPeriodPeakEnergy, mEnergyAverage);
                mPeriodSumEnergy += mEnergyAverage;
                mPeriodSampleCount++;
            }
            else
            {
                if(mSignalStartSample != null)
                {
                    // End of signal period
                    closeSignalPeriod(mSampleCount, true);
                }
            }

            mSampleCount++;
        }
    }

    /**
     * Close the current signal period and add to list.
     */
    private void closeSignalPeriod(long endSample, boolean isComplete)
    {
        if(mSignalStartSample == null) return;

        long startMs = samplesToMs(mSignalStartSample);
        long endMs = samplesToMs(endSample);
        float avgEnergy = mPeriodSampleCount > 0 ? (float)(mPeriodSumEnergy / mPeriodSampleCount) : 0;

        mSignalPeriods.add(new SignalPeriod(startMs, endMs, mPeriodPeakEnergy, avgEnergy, isComplete));
        mSignalStartSample = null;
    }

    /**
     * Convert sample count to milliseconds.
     */
    private long samplesToMs(long samples)
    {
        if(mSampleRate <= 0)
        {
            return 0;
        }
        return (long)((samples / mSampleRate) * 1000.0);
    }

    /**
     * Convert signal periods to transmission records, merging periods with small gaps.
     */
    private List<Transmission> convertToTransmissions()
    {
        List<Transmission> transmissions = new ArrayList<>();

        if(mSignalPeriods.isEmpty())
        {
            return transmissions;
        }

        // Merge signal periods that are close together
        List<SignalPeriod> merged = new ArrayList<>();
        SignalPeriod current = mSignalPeriods.get(0);

        for(int i = 1; i < mSignalPeriods.size(); i++)
        {
            SignalPeriod next = mSignalPeriods.get(i);
            long gap = next.startMs - current.endMs;

            if(gap < MIN_GAP_MS)
            {
                // Merge: extend current period
                current = new SignalPeriod(
                    current.startMs,
                    next.endMs,
                    Math.max(current.peakEnergy, next.peakEnergy),
                    (current.avgEnergy + next.avgEnergy) / 2, // simple average
                    next.isComplete
                );
            }
            else
            {
                // Gap too large: save current and start new
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        // Filter out short periods and convert to transmissions
        int index = 1;
        for(SignalPeriod period : merged)
        {
            long duration = period.endMs - period.startMs;
            if(duration >= MIN_TRANSMISSION_MS)
            {
                transmissions.add(new Transmission(
                    index++,
                    period.startMs,
                    period.endMs,
                    period.peakEnergy,
                    period.avgEnergy,
                    period.isComplete
                ));
            }
        }

        return transmissions;
    }

    /**
     * Get the sample rate of the last processed file.
     */
    public double getSampleRate()
    {
        return mSampleRate;
    }

    /**
     * Get the total duration of the last processed file in milliseconds.
     */
    public long getTotalDurationMs()
    {
        return samplesToMs(mSampleCount);
    }

    /**
     * Internal record for tracking signal periods during detection.
     */
    private record SignalPeriod(long startMs, long endMs, float peakEnergy, float avgEnergy, boolean isComplete) {}
}
