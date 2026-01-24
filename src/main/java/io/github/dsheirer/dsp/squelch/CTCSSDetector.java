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
package io.github.dsheirer.dsp.squelch;

/**
 * CTCSS (PL) tone detector using the Goertzel algorithm. Detects the presence of a specific sub-audible tone
 * frequency in demodulated FM audio. Uses hysteresis to avoid rapid toggling of the detection state.
 *
 * The detector accumulates samples into blocks and evaluates each block for the presence of the target tone.
 * Block size is set to 250ms which provides sufficient frequency resolution (~4 Hz) to distinguish adjacent
 * CTCSS tones while the center bin's main lobe naturally covers the ±2% frequency tolerance.
 * A configurable number of consecutive positive/negative detections are required before the state changes.
 */
public class CTCSSDetector
{
    private static final int DEFAULT_HYSTERESIS_OPEN = 2;
    private static final int DEFAULT_HYSTERESIS_CLOSE = 3;
    private static final double DETECTION_THRESHOLD_DB = -10.0;

    private final double mTargetFrequency;
    private double mSampleRate;
    private int mBlockSize;
    private double mCoefficient;
    private double mS;
    private double mSPrev;
    private double mSPrev2;
    private int mSampleCount;
    private boolean mToneDetected;
    private int mHysteresisCount;
    private int mHysteresisOpenThreshold = DEFAULT_HYSTERESIS_OPEN;
    private int mHysteresisCloseThreshold = DEFAULT_HYSTERESIS_CLOSE;
    private Runnable mToneDetectedListener;

    /**
     * Constructs an instance
     * @param targetFrequency the CTCSS tone frequency to detect in Hz
     */
    public CTCSSDetector(double targetFrequency)
    {
        mTargetFrequency = targetFrequency;
    }

    /**
     * Sets the sample rate and recalculates internal parameters.
     * Block size is set to 250ms which places the Goertzel first null at the adjacent CTCSS tone spacing (~4 Hz),
     * providing clean rejection of adjacent tones while the main lobe covers ±2% frequency tolerance.
     * @param sampleRate of the incoming demodulated audio stream
     */
    public void setSampleRate(double sampleRate)
    {
        mSampleRate = sampleRate;

        // Block size targets 250ms for sufficient frequency resolution to reject adjacent CTCSS tones.
        // At 250ms, the first null is at sampleRate/blockSize Hz offset from center, which for typical
        // sample rates gives ~4 Hz resolution matching the minimum CTCSS tone spacing.
        mBlockSize = (int)(sampleRate * 0.25);

        // Ensure block size gives at least 10 full cycles of the target frequency for accuracy
        int minimumBlockSize = (int)(10.0 * sampleRate / mTargetFrequency);
        if(mBlockSize < minimumBlockSize)
        {
            mBlockSize = minimumBlockSize;
        }

        // Pre-calculate Goertzel coefficient for the target frequency
        double normalizedFrequency = mTargetFrequency / sampleRate;
        mCoefficient = 2.0 * Math.cos(2.0 * Math.PI * normalizedFrequency);

        reset();
    }

    /**
     * Resets the detector state
     */
    private void reset()
    {
        mS = 0;
        mSPrev = 0;
        mSPrev2 = 0;
        mSampleCount = 0;
    }

    /**
     * Sets a listener that is notified each time the tone transitions from not-detected to detected.
     * @param listener to invoke on detection, or null to remove.
     */
    public void setToneDetectedListener(Runnable listener)
    {
        mToneDetectedListener = listener;
    }

    /**
     * Indicates if the CTCSS tone is currently detected
     * @return true if the tone is detected with sufficient confidence
     */
    public boolean isToneDetected()
    {
        return mToneDetected;
    }

    /**
     * Processes a buffer of demodulated audio samples for CTCSS tone detection.
     * @param samples demodulated audio (pre-noise-squelch, pre-high-pass-filter)
     */
    public void process(float[] samples)
    {
        for(float sample : samples)
        {
            // Goertzel iteration for center frequency
            mS = sample + (mCoefficient * mSPrev) - mSPrev2;
            mSPrev2 = mSPrev;
            mSPrev = mS;

            mSampleCount++;

            if(mSampleCount >= mBlockSize)
            {
                evaluateBlock();
                resetGoertzelState();
                mSampleCount = 0;
            }
        }
    }

    /**
     * Evaluates the current block for tone presence using the Goertzel magnitude result.
     * Compares the tone energy at the target frequency against a fixed reference level
     * calibrated for typical CTCSS tone amplitudes.
     */
    private void evaluateBlock()
    {
        double magnitude = getMagnitudeSquared(mSPrev, mSPrev2, mCoefficient);

        // Fixed reference level scaled to block size
        double totalPower = (double)mBlockSize * mBlockSize * 0.01;

        boolean tonePresent;
        if(totalPower <= 0)
        {
            tonePresent = false;
        }
        else
        {
            double ratio = 10.0 * Math.log10(magnitude / totalPower);
            tonePresent = ratio > DETECTION_THRESHOLD_DB;
        }

        // Apply hysteresis
        if(tonePresent)
        {
            mHysteresisCount = Math.min(mHysteresisCount + 1, mHysteresisCloseThreshold);
        }
        else
        {
            mHysteresisCount = Math.max(mHysteresisCount - 1, 0);
        }

        if(!mToneDetected && mHysteresisCount >= mHysteresisOpenThreshold)
        {
            mToneDetected = true;

            if(mToneDetectedListener != null)
            {
                mToneDetectedListener.run();
            }
        }
        else if(mToneDetected && mHysteresisCount <= 0)
        {
            mToneDetected = false;
        }
    }

    /**
     * Calculates the magnitude squared from the Goertzel state variables
     */
    private double getMagnitudeSquared(double sPrev, double sPrev2, double coefficient)
    {
        return (sPrev * sPrev) + (sPrev2 * sPrev2) - (coefficient * sPrev * sPrev2);
    }

    /**
     * Resets the Goertzel state variables for the next block
     */
    private void resetGoertzelState()
    {
        mS = 0;
        mSPrev = 0;
        mSPrev2 = 0;
    }
}
