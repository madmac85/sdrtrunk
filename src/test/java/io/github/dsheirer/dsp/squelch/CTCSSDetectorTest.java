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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the CTCSSDetector class.
 */
public class CTCSSDetectorTest
{
    private static final double SAMPLE_RATE = 25000.0;
    private static final double TARGET_FREQUENCY = 114.8;
    private static final float TONE_AMPLITUDE = 0.1f; // Typical CTCSS tone is ~10-15% of deviation
    private CTCSSDetector mDetector;

    @BeforeEach
    void setUp()
    {
        mDetector = new CTCSSDetector(TARGET_FREQUENCY);
        mDetector.setSampleRate(SAMPLE_RATE);
    }

    /**
     * Generates a sine wave at the specified frequency and amplitude.
     */
    private float[] generateTone(double frequency, float amplitude, double durationSeconds)
    {
        int numSamples = (int)(SAMPLE_RATE * durationSeconds);
        float[] samples = new float[numSamples];
        for(int i = 0; i < numSamples; i++)
        {
            samples[i] = (float)(amplitude * Math.sin(2.0 * Math.PI * frequency * i / SAMPLE_RATE));
        }
        return samples;
    }

    /**
     * Generates white noise at the specified amplitude.
     */
    private float[] generateNoise(float amplitude, double durationSeconds)
    {
        int numSamples = (int)(SAMPLE_RATE * durationSeconds);
        float[] samples = new float[numSamples];
        for(int i = 0; i < numSamples; i++)
        {
            samples[i] = (float)((Math.random() * 2.0 - 1.0) * amplitude);
        }
        return samples;
    }

    @Test
    void testDetectsTargetFrequency()
    {
        // Feed 1 second of 114.8 Hz tone - should be detected after hysteresis period
        float[] tone = generateTone(TARGET_FREQUENCY, TONE_AMPLITUDE, 1.0);
        mDetector.process(tone);
        assertTrue(mDetector.isToneDetected(), "Should detect 114.8 Hz tone");
    }

    @Test
    void testDoesNotDetectDifferentFrequency()
    {
        // Feed 1 second of 127.3 Hz tone - should NOT be detected as 114.8 Hz
        float[] tone = generateTone(127.3, TONE_AMPLITUDE, 1.0);
        mDetector.process(tone);
        assertFalse(mDetector.isToneDetected(), "Should not detect 127.3 Hz when looking for 114.8 Hz");
    }

    @Test
    void testDetectsWithinPositiveTolerance()
    {
        // Feed 1 second of tone at +1.5% of target (within 2% tolerance)
        double shiftedFrequency = TARGET_FREQUENCY * 1.015;
        float[] tone = generateTone(shiftedFrequency, TONE_AMPLITUDE, 1.0);
        mDetector.process(tone);
        assertTrue(mDetector.isToneDetected(),
            "Should detect tone within +1.5% tolerance (freq=" + shiftedFrequency + " Hz)");
    }

    @Test
    void testDetectsWithinNegativeTolerance()
    {
        // Feed 1 second of tone at -1.5% of target (within 2% tolerance)
        double shiftedFrequency = TARGET_FREQUENCY * 0.985;
        float[] tone = generateTone(shiftedFrequency, TONE_AMPLITUDE, 1.0);
        mDetector.process(tone);
        assertTrue(mDetector.isToneDetected(),
            "Should detect tone within -1.5% tolerance (freq=" + shiftedFrequency + " Hz)");
    }

    @Test
    void testNoFalsePositiveFromNoise()
    {
        // Feed 1 second of white noise - should not trigger detection
        float[] noise = generateNoise(0.3f, 1.0);
        mDetector.process(noise);
        assertFalse(mDetector.isToneDetected(), "Should not detect tone in white noise");
    }

    @Test
    void testInitialStateIsNotDetected()
    {
        assertFalse(mDetector.isToneDetected(), "Initial state should be not-detected");
    }

    @Test
    void testHysteresisPreventsInstantDetection()
    {
        // Feed only 30ms of tone - should NOT be enough to trigger detection (hysteresis requires ~200ms)
        float[] shortTone = generateTone(TARGET_FREQUENCY, TONE_AMPLITUDE, 0.03);
        mDetector.process(shortTone);
        assertFalse(mDetector.isToneDetected(), "30ms of tone should not be enough to trigger detection");
    }

    @Test
    void testToneDetectionThenLoss()
    {
        // First, establish detection with 1 second of tone
        float[] tone = generateTone(TARGET_FREQUENCY, TONE_AMPLITUDE, 1.0);
        mDetector.process(tone);
        assertTrue(mDetector.isToneDetected(), "Should detect tone after 1 second");

        // Then feed silence/noise for 1 second - should lose detection
        float[] silence = new float[(int)(SAMPLE_RATE * 1.0)];
        mDetector.process(silence);
        assertFalse(mDetector.isToneDetected(), "Should lose detection after 1 second of silence");
    }

    @Test
    void testToneWithVoiceAudio()
    {
        // Simulate real-world: CTCSS tone + voice audio (higher frequency content)
        int numSamples = (int)(SAMPLE_RATE * 1.0);
        float[] combined = new float[numSamples];
        for(int i = 0; i < numSamples; i++)
        {
            // CTCSS tone at typical level
            double ctcssTone = TONE_AMPLITUDE * Math.sin(2.0 * Math.PI * TARGET_FREQUENCY * i / SAMPLE_RATE);
            // Voice audio simulation (1 kHz + 2 kHz components, higher amplitude)
            double voice = 0.5 * Math.sin(2.0 * Math.PI * 1000.0 * i / SAMPLE_RATE)
                         + 0.3 * Math.sin(2.0 * Math.PI * 2000.0 * i / SAMPLE_RATE);
            combined[i] = (float)(ctcssTone + voice);
        }
        mDetector.process(combined);
        assertTrue(mDetector.isToneDetected(), "Should detect CTCSS tone even with voice audio present");
    }

    @Test
    void testDoesNotDetectAdjacentCTCSSTone()
    {
        // The next CTCSS tone above 114.8 is 118.8 Hz (~3.5% away, outside 2% tolerance)
        float[] adjacentTone = generateTone(118.8, TONE_AMPLITUDE, 1.0);
        mDetector.process(adjacentTone);
        assertFalse(mDetector.isToneDetected(), "Should not detect adjacent CTCSS tone 118.8 Hz");
    }
}
