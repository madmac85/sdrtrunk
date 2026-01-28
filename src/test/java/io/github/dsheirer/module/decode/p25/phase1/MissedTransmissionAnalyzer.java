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

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes energy profiles to identify periods where signal was present but no audio was decoded.
 * Uses similar energy detection logic to P25P1DecoderLSMv2 for consistency.
 */
public class MissedTransmissionAnalyzer
{
    // Energy detection parameters matching P25P1DecoderLSMv2
    private static final float ENERGY_EMA_FACTOR = 0.001f;
    private static final float ENERGY_SILENCE_RATIO = 0.10f;
    private static final float PEAK_DECAY = 0.99999f;

    // Minimum gap duration to consider as a missed transmission (ms)
    private static final long MIN_GAP_DURATION_MS = 300;

    // Tolerance for matching LDU timestamps to energy periods (ms)
    private static final long TIMESTAMP_TOLERANCE_MS = 200;

    /**
     * Represents a period where high energy was detected but no LDU was decoded.
     */
    public record MissedTransmission(
        long startMs,
        long endMs,
        double peakEnergy,
        double avgEnergy,
        int samplesInPeriod
    ) {
        public long durationMs() {
            return endMs - startMs;
        }
    }

    /**
     * Tracks energy levels over time for analysis.
     */
    public static class EnergyProfile
    {
        private final List<EnergyPoint> mPoints = new ArrayList<>();
        private float mEnergyAverage = 0;
        private float mPeakEnergy = 0;
        private long mSampleCount = 0;
        private final double mSampleRate;

        public EnergyProfile(double sampleRate)
        {
            mSampleRate = sampleRate;
        }

        /**
         * Adds I/Q samples to the energy profile.
         */
        public void addSamples(float[] i, float[] q)
        {
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

                // Sample periodically to reduce memory usage (every 100 samples = ~4ms at 25 kHz)
                if(mSampleCount % 100 == 0)
                {
                    long timestampMs = (long)((mSampleCount / mSampleRate) * 1000.0);
                    float silenceThreshold = mPeakEnergy * ENERGY_SILENCE_RATIO;
                    boolean isSignal = mPeakEnergy > 0 && mEnergyAverage >= silenceThreshold;
                    mPoints.add(new EnergyPoint(timestampMs, mEnergyAverage, mPeakEnergy, isSignal));
                }
                mSampleCount++;
            }
        }

        public List<EnergyPoint> getPoints()
        {
            return mPoints;
        }

        public double getSampleRate()
        {
            return mSampleRate;
        }

        public long getTotalDurationMs()
        {
            return (long)((mSampleCount / mSampleRate) * 1000.0);
        }
    }

    /**
     * A single energy measurement point.
     */
    public record EnergyPoint(long timestampMs, float energy, float peakEnergy, boolean isSignal) {}

    /**
     * Analyzes energy profile against LDU timestamps to find missed transmissions.
     *
     * @param profile the energy profile from the recording
     * @param lduTimestamps list of timestamps (ms) when LDUs were decoded
     * @param baseTimestamp the base timestamp to normalize LDU timestamps
     * @return list of missed transmission periods
     */
    public List<MissedTransmission> analyze(EnergyProfile profile, List<Long> lduTimestamps, long baseTimestamp)
    {
        List<MissedTransmission> missed = new ArrayList<>();
        List<EnergyPoint> points = profile.getPoints();

        if(points.isEmpty())
        {
            return missed;
        }

        // Normalize LDU timestamps to be relative to start
        List<Long> normalizedLduTimestamps = lduTimestamps.stream()
            .map(ts -> ts - baseTimestamp)
            .toList();

        // Find contiguous signal periods
        List<SignalPeriod> signalPeriods = findSignalPeriods(points);

        // For each signal period, check if there's a corresponding LDU
        for(SignalPeriod period : signalPeriods)
        {
            if(period.durationMs() < MIN_GAP_DURATION_MS)
            {
                continue; // Too short to be a real transmission
            }

            boolean hasLdu = false;
            for(Long lduTs : normalizedLduTimestamps)
            {
                // Check if LDU timestamp falls within or near this signal period
                if(lduTs >= period.startMs - TIMESTAMP_TOLERANCE_MS &&
                   lduTs <= period.endMs + TIMESTAMP_TOLERANCE_MS)
                {
                    hasLdu = true;
                    break;
                }
            }

            if(!hasLdu)
            {
                missed.add(new MissedTransmission(
                    period.startMs,
                    period.endMs,
                    period.peakEnergy,
                    period.avgEnergy,
                    period.sampleCount
                ));
            }
        }

        return missed;
    }

    /**
     * Finds contiguous periods where signal was detected.
     */
    private List<SignalPeriod> findSignalPeriods(List<EnergyPoint> points)
    {
        List<SignalPeriod> periods = new ArrayList<>();

        Long periodStart = null;
        double sumEnergy = 0;
        double maxEnergy = 0;
        int sampleCount = 0;

        for(EnergyPoint point : points)
        {
            if(point.isSignal())
            {
                if(periodStart == null)
                {
                    periodStart = point.timestampMs();
                    sumEnergy = 0;
                    maxEnergy = 0;
                    sampleCount = 0;
                }
                sumEnergy += point.energy();
                maxEnergy = Math.max(maxEnergy, point.energy());
                sampleCount++;
            }
            else
            {
                if(periodStart != null)
                {
                    double avgEnergy = sampleCount > 0 ? sumEnergy / sampleCount : 0;
                    periods.add(new SignalPeriod(periodStart, point.timestampMs(), maxEnergy, avgEnergy, sampleCount));
                    periodStart = null;
                }
            }
        }

        // Handle case where signal extends to end of recording
        if(periodStart != null && !points.isEmpty())
        {
            EnergyPoint last = points.get(points.size() - 1);
            double avgEnergy = sampleCount > 0 ? sumEnergy / sampleCount : 0;
            periods.add(new SignalPeriod(periodStart, last.timestampMs(), maxEnergy, avgEnergy, sampleCount));
        }

        return periods;
    }

    /**
     * Internal record for tracking signal periods during analysis.
     */
    private record SignalPeriod(long startMs, long endMs, double peakEnergy, double avgEnergy, int sampleCount)
    {
        long durationMs() { return endMs - startMs; }
    }

    /**
     * Compares missed transmissions between two decoders to find v2-specific issues.
     *
     * @param lsmMissed missed transmissions from LSM decoder
     * @param v2Missed missed transmissions from LSM v2 decoder
     * @return list of transmissions that v2 missed but LSM caught
     */
    public List<MissedTransmission> findV2Regressions(
        List<MissedTransmission> lsmMissed,
        List<MissedTransmission> v2Missed)
    {
        List<MissedTransmission> regressions = new ArrayList<>();

        for(MissedTransmission v2Miss : v2Missed)
        {
            boolean lsmAlsoMissed = false;
            for(MissedTransmission lsmMiss : lsmMissed)
            {
                // Check for overlap
                if(v2Miss.startMs() <= lsmMiss.endMs() + TIMESTAMP_TOLERANCE_MS &&
                   v2Miss.endMs() >= lsmMiss.startMs() - TIMESTAMP_TOLERANCE_MS)
                {
                    lsmAlsoMissed = true;
                    break;
                }
            }
            if(!lsmAlsoMissed)
            {
                regressions.add(v2Miss);
            }
        }

        return regressions;
    }
}
