/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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

package io.github.dsheirer.dsp.filter.equalizer;

/**
 * Constant Modulus Algorithm (CMA) blind adaptive equalizer for CQPSK signals.
 *
 * Compensates for inter-symbol interference (ISI) caused by multipath propagation
 * in simulcast P25 systems. Operates on complex I/Q sample pairs before differential
 * demodulation. The CMA cost function drives the equalized output toward constant
 * envelope (all symbols on the unit circle), which is the expected property of CQPSK.
 *
 * Uses batch float-array processing for performance in the hot DSP path.
 */
public class CMAEqualizer
{
    public static final int TAP_COUNT = 11;

    private final float mModulus;
    private float mMu;
    private boolean mEnabled = true;

    // Gear-shifting: start with fast convergence, then reduce to tracking
    private float mAcquisitionMu;
    private float mTrackingMu;
    private int mSampleCount = 0;
    private int mGearShiftSamples = 0;  // 0 = disabled (fixed mu)

    // Tap coefficients
    private final float[] mTapsI = new float[TAP_COUNT];
    private final float[] mTapsQ = new float[TAP_COUNT];

    // Circular buffer for input samples
    private final float[] mBufferI = new float[TAP_COUNT];
    private final float[] mBufferQ = new float[TAP_COUNT];
    private int mBufferPointer = 0;

    /**
     * Constructs a CMA equalizer.
     *
     * @param modulus target output magnitude (1.0 for CQPSK unit circle)
     * @param mu step size controlling convergence speed vs stability (typical: 0.0005-0.005)
     */
    public CMAEqualizer(float modulus, float mu)
    {
        mModulus = modulus;
        mMu = mu;
        mAcquisitionMu = mu;
        mTrackingMu = mu;
        reset();
    }

    /**
     * Resets the equalizer to initial state (passthrough). Call on cold-start / transmission boundary.
     * First tap set to (1,0) — output equals input until adaptation begins.
     * If gear-shifting is configured, resets to acquisition mu.
     */
    public void reset()
    {
        mTapsI[0] = 1.0f;
        mTapsQ[0] = 0.0f;

        for(int x = 1; x < TAP_COUNT; x++)
        {
            mTapsI[x] = 0.0f;
            mTapsQ[x] = 0.0f;
        }

        for(int x = 0; x < TAP_COUNT; x++)
        {
            mBufferI[x] = 0.0f;
            mBufferQ[x] = 0.0f;
        }

        mBufferPointer = 0;
        mSampleCount = 0;

        // Reset to acquisition mu if gear-shifting is configured
        if(mGearShiftSamples > 0)
        {
            mMu = mAcquisitionMu;
        }
    }

    /**
     * Enables or disables the equalizer. When disabled, equalize() is a no-op passthrough.
     */
    public void setEnabled(boolean enabled)
    {
        mEnabled = enabled;
    }

    /**
     * @return true if the equalizer is enabled
     */
    public boolean isEnabled()
    {
        return mEnabled;
    }

    /**
     * Sets the step size (mu) for CMA adaptation.
     */
    public void setMu(float mu)
    {
        mMu = mu;
    }

    /**
     * @return the current step size
     */
    public float getMu()
    {
        return mMu;
    }

    /**
     * Configures gear-shifting: start with acquisitionMu for fast convergence after reset,
     * then reduce to trackingMu after the specified number of samples for low-distortion tracking.
     *
     * @param acquisitionMu step size for initial convergence (larger, e.g. 0.005)
     * @param trackingMu step size for steady-state tracking (smaller, e.g. 0.001)
     * @param gearShiftSamples number of samples before switching from acquisition to tracking
     */
    public void setGearShift(float acquisitionMu, float trackingMu, int gearShiftSamples)
    {
        mAcquisitionMu = acquisitionMu;
        mTrackingMu = trackingMu;
        mGearShiftSamples = gearShiftSamples;
        mMu = acquisitionMu;
    }

    /**
     * Equalizes I/Q sample arrays in-place using the CMA algorithm.
     *
     * For each sample pair (iSamples[n], qSamples[n]):
     * 1. Insert into circular buffer
     * 2. Apply FIR filter using current tap weights → equalized output
     * 3. Compute CMA error: e = y * (|y|^2 - R) where R is the target modulus
     * 4. Update taps: w -= mu * conj(x) * e
     * 5. Write equalized output back to input arrays
     *
     * @param iSamples in-phase samples (modified in-place)
     * @param qSamples quadrature samples (modified in-place)
     */
    public void equalize(float[] iSamples, float[] qSamples)
    {
        if(!mEnabled)
        {
            return;
        }

        float mu = mMu;
        float modulus = mModulus;
        int bufferPointer = mBufferPointer;
        int sampleCount = mSampleCount;
        int gearShiftSamples = mGearShiftSamples;
        float trackingMu = mTrackingMu;

        for(int n = 0; n < iSamples.length; n++)
        {
            // 1. Insert sample into circular buffer
            mBufferI[bufferPointer] = iSamples[n];
            mBufferQ[bufferPointer] = qSamples[n];

            bufferPointer = (bufferPointer + 1) % TAP_COUNT;

            // 2. FIR filter: y = sum(w[k] * x[n-k])
            float yI = 0.0f;
            float yQ = 0.0f;

            for(int k = 0; k < TAP_COUNT; k++)
            {
                int idx = (bufferPointer + k) % TAP_COUNT;
                // Complex multiply: (tapI + j*tapQ) * (bufI + j*bufQ)
                yI += mTapsI[k] * mBufferI[idx] - mTapsQ[k] * mBufferQ[idx];
                yQ += mTapsI[k] * mBufferQ[idx] + mTapsQ[k] * mBufferI[idx];
            }

            // 3. CMA error: e = y * (|y|^2 - R)
            float norm = yI * yI + yQ * yQ;
            float errorScale = norm - modulus;
            float errorI = yI * errorScale;
            float errorQ = yQ * errorScale;

            // Clip error to prevent instability
            float errorMag = errorI * errorI + errorQ * errorQ;
            if(errorMag > 1.0f)
            {
                float invMag = 1.0f / (float)Math.sqrt(errorMag);
                errorI *= invMag;
                errorQ *= invMag;
            }

            // 4. Update taps: w[k] -= mu * conj(x[n-k]) * e
            for(int k = 0; k < TAP_COUNT; k++)
            {
                int idx = (bufferPointer + k) % TAP_COUNT;
                // conj(x) * e = (xI - j*xQ) * (eI + j*eQ)
                float conjXeI = mBufferI[idx] * errorI + mBufferQ[idx] * errorQ;
                float conjXeQ = mBufferI[idx] * errorQ - mBufferQ[idx] * errorI;
                mTapsI[k] -= mu * conjXeI;
                mTapsQ[k] -= mu * conjXeQ;
            }

            // 5. Write equalized output
            iSamples[n] = yI;
            qSamples[n] = yQ;

            // Gear-shifting: switch from acquisition to tracking mu
            sampleCount++;
            if(gearShiftSamples > 0 && sampleCount == gearShiftSamples)
            {
                mu = trackingMu;
            }
        }

        mBufferPointer = bufferPointer;
        mSampleCount = sampleCount;
        if(gearShiftSamples > 0 && sampleCount >= gearShiftSamples)
        {
            mMu = trackingMu;
        }
    }
}
