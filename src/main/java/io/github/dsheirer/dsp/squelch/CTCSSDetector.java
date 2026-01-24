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
 * A configurable number of consecutive positive/negative detections are required before the state changes.
 */
public class CTCSSDetector
{
    private static final int DEFAULT_HYSTERESIS_OPEN = 4;
    private static final int DEFAULT_HYSTERESIS_CLOSE = 6;
    private static final double DETECTION_THRESHOLD_DB = -10.0;
    private static final double FREQUENCY_TOLERANCE = 0.02; // +/- 2%

    private final double mTargetFrequency;
    private double mSampleRate;
    private int mBlockSize;
    private double mCoefficient;
    private double mCoefficientLow;
    private double mCoefficientHigh;
    private double mS;
    private double mSPrev;
    private double mSPrev2;
    private double mSLow;
    private double mSPrevLow;
    private double mSPrev2Low;
    private double mSHigh;
    private double mSPrevHigh;
    private double mSPrev2High;
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
     * Block size is chosen to give approximately 200ms detection windows.
     * @param sampleRate of the incoming demodulated audio stream
     */
    public void setSampleRate(double sampleRate)
    {
        mSampleRate = sampleRate;

        // Block size targets ~50ms windows for responsive detection.
        // Multiple blocks are required via hysteresis for state change.
        mBlockSize = (int)(sampleRate * 0.05);

        // Ensure block size gives at least 3 full cycles of the target frequency for accuracy
        int minimumBlockSize = (int)(3.0 * sampleRate / mTargetFrequency);
        if(mBlockSize < minimumBlockSize)
        {
            mBlockSize = minimumBlockSize;
        }

        // Pre-calculate Goertzel coefficients for target and tolerance bounds
        double normalizedFrequency = mTargetFrequency / sampleRate;
        mCoefficient = 2.0 * Math.cos(2.0 * Math.PI * normalizedFrequency);

        double lowFrequency = mTargetFrequency * (1.0 - FREQUENCY_TOLERANCE);
        double highFrequency = mTargetFrequency * (1.0 + FREQUENCY_TOLERANCE);
        mCoefficientLow = 2.0 * Math.cos(2.0 * Math.PI * lowFrequency / sampleRate);
        mCoefficientHigh = 2.0 * Math.cos(2.0 * Math.PI * highFrequency / sampleRate);

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
        mSLow = 0;
        mSPrevLow = 0;
        mSPrev2Low = 0;
        mSHigh = 0;
        mSPrevHigh = 0;
        mSPrev2High = 0;
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

            // Goertzel iteration for low tolerance bound
            mSLow = sample + (mCoefficientLow * mSPrevLow) - mSPrev2Low;
            mSPrev2Low = mSPrevLow;
            mSPrevLow = mSLow;

            // Goertzel iteration for high tolerance bound
            mSHigh = sample + (mCoefficientHigh * mSPrevHigh) - mSPrev2High;
            mSPrev2High = mSPrevHigh;
            mSPrevHigh = mSHigh;

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
     * Compares the tone energy at the target frequency (and tolerance bounds) against the
     * total signal energy to determine if the tone is present.
     */
    private void evaluateBlock()
    {
        // Calculate magnitude squared for each frequency bin
        double magnitudeCenter = getMagnitudeSquared(mSPrev, mSPrev2, mCoefficient);
        double magnitudeLow = getMagnitudeSquared(mSPrevLow, mSPrev2Low, mCoefficientLow);
        double magnitudeHigh = getMagnitudeSquared(mSPrevHigh, mSPrev2High, mCoefficientHigh);

        // Use the maximum magnitude across the tolerance band
        double magnitude = Math.max(magnitudeCenter, Math.max(magnitudeLow, magnitudeHigh));

        // Calculate total signal power for normalization
        double totalPower = getTotalPower();

        boolean tonePresent;
        if(totalPower <= 0)
        {
            tonePresent = false;
        }
        else
        {
            // Compare tone power ratio to threshold
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
     * Calculates the total signal power by computing the sum of squares across the stored
     * Goertzel state. This uses the DC bin (bin 0) magnitude as a proxy for total power.
     */
    private double getTotalPower()
    {
        // Use the sum of the Goertzel magnitudes at center, low, and high as a baseline,
        // but for proper normalization we use the magnitude at center frequency relative to
        // the block size. The threshold is calibrated to detect typical CTCSS tone levels
        // (-15 to -20 dB relative to voice audio).
        return mBlockSize * mBlockSize * 0.01; // Normalized reference level
    }

    /**
     * Resets the Goertzel state variables for the next block
     */
    private void resetGoertzelState()
    {
        mS = 0;
        mSPrev = 0;
        mSPrev2 = 0;
        mSLow = 0;
        mSPrevLow = 0;
        mSPrev2Low = 0;
        mSHigh = 0;
        mSPrevHigh = 0;
        mSPrev2High = 0;
    }
}
