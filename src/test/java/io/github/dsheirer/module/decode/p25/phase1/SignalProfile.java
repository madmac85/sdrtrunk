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

import java.util.Arrays;

/**
 * Detailed signal characterization for sync failure analysis.
 * Provides metrics beyond simple average energy to understand signal behavior.
 */
public record SignalProfile(
    float[] energyEnvelope,    // Energy samples over time (sampled at ~10ms intervals)
    float avgEnergy,           // Simple average energy
    float energyVariance,      // Stability measure (std dev of energy)
    float preambleEnergy,      // First 100ms average
    float energySlope,         // Rate of change at transmission start (dB/100ms)
    float peakToAverage,       // Peak energy divided by average
    float snrEstimate          // Estimated signal-to-noise ratio (dB)
)
{
    /**
     * Creates a SignalProfile from raw sample data.
     *
     * @param samples Raw baseband samples
     * @param sampleRate Sample rate in Hz
     * @param noiseFloor Estimated noise floor for SNR calculation
     * @return SignalProfile with calculated metrics
     */
    public static SignalProfile fromSamples(float[] samples, int sampleRate, float noiseFloor)
    {
        if(samples == null || samples.length == 0)
        {
            return new SignalProfile(new float[0], 0, 0, 0, 0, 0, 0);
        }

        // Calculate energy envelope at ~10ms intervals
        int windowSize = sampleRate / 100; // 10ms window
        int envelopeLength = Math.max(1, samples.length / windowSize);
        float[] envelope = new float[envelopeLength];

        float totalEnergy = 0;
        float peakEnergy = 0;

        for(int i = 0; i < envelopeLength; i++)
        {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);
            float windowEnergy = calculateWindowEnergy(samples, start, end);
            envelope[i] = windowEnergy;
            totalEnergy += windowEnergy;
            peakEnergy = Math.max(peakEnergy, windowEnergy);
        }

        float avgEnergy = envelopeLength > 0 ? totalEnergy / envelopeLength : 0;

        // Calculate variance
        float variance = calculateVariance(envelope, avgEnergy);

        // Calculate preamble energy (first 100ms = first 10 windows)
        int preambleWindows = Math.min(10, envelopeLength);
        float preambleEnergy = 0;
        for(int i = 0; i < preambleWindows; i++)
        {
            preambleEnergy += envelope[i];
        }
        preambleEnergy = preambleWindows > 0 ? preambleEnergy / preambleWindows : 0;

        // Calculate energy slope at start (first 100ms)
        float energySlope = calculateEnergySlope(envelope, preambleWindows);

        // Peak to average ratio
        float peakToAverage = avgEnergy > 0 ? peakEnergy / avgEnergy : 0;

        // SNR estimate (dB)
        float snrEstimate = noiseFloor > 0 ? 10 * (float)Math.log10(avgEnergy / noiseFloor) : 0;

        return new SignalProfile(envelope, avgEnergy, variance, preambleEnergy, energySlope, peakToAverage, snrEstimate);
    }

    /**
     * Creates a SignalProfile from pre-calculated energy values.
     * Used when detailed sample data is not available.
     *
     * @param avgEnergy Average energy from transmission
     * @param peakEnergy Peak energy from transmission
     * @param durationMs Duration in milliseconds
     * @return SignalProfile with estimated metrics
     */
    public static SignalProfile fromEnergyValues(float avgEnergy, float peakEnergy, long durationMs)
    {
        // Create synthetic envelope based on available data
        int envelopeLength = (int)(durationMs / 10); // One sample per 10ms
        float[] envelope = new float[Math.max(1, envelopeLength)];
        Arrays.fill(envelope, avgEnergy);

        float peakToAverage = avgEnergy > 0 ? peakEnergy / avgEnergy : 0;

        // Without raw samples, we estimate conservative values
        float variance = avgEnergy * 0.1f; // Assume 10% variance
        float preambleEnergy = avgEnergy; // Assume uniform energy
        float energySlope = 0; // Unknown without raw data
        float snrEstimate = 0; // Cannot estimate without noise floor

        return new SignalProfile(envelope, avgEnergy, variance, preambleEnergy, energySlope, peakToAverage, snrEstimate);
    }

    private static float calculateWindowEnergy(float[] samples, int start, int end)
    {
        float energy = 0;
        for(int i = start; i < end; i++)
        {
            energy += samples[i] * samples[i];
        }
        return (end - start) > 0 ? energy / (end - start) : 0;
    }

    private static float calculateVariance(float[] values, float mean)
    {
        if(values.length < 2) return 0;

        float sumSquaredDiff = 0;
        for(float value : values)
        {
            float diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return (float)Math.sqrt(sumSquaredDiff / values.length);
    }

    private static float calculateEnergySlope(float[] envelope, int windowCount)
    {
        if(windowCount < 2) return 0;

        // Simple linear regression over first windowCount samples
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = Math.min(windowCount, envelope.length);

        for(int i = 0; i < n; i++)
        {
            sumX += i;
            sumY += envelope[i];
            sumXY += i * envelope[i];
            sumX2 += i * i;
        }

        float denominator = n * sumX2 - sumX * sumX;
        if(Math.abs(denominator) < 0.0001f) return 0;

        // Slope in energy units per 10ms window, convert to dB/100ms
        float slope = (n * sumXY - sumX * sumY) / denominator;
        return slope * 10; // Scale to per 100ms
    }

    /**
     * Returns true if preamble energy is significantly weaker than average.
     * Threshold: preamble < 70% of average energy.
     */
    public boolean hasWeakPreamble()
    {
        return avgEnergy > 0 && preambleEnergy < avgEnergy * 0.7f;
    }

    /**
     * Returns true if signal shows high variance (unstable).
     * Threshold: variance > 30% of average energy.
     */
    public boolean hasHighVariance()
    {
        return avgEnergy > 0 && energyVariance > avgEnergy * 0.3f;
    }

    /**
     * Returns true if signal shows rapid fade at start (negative slope).
     * Threshold: slope < -2 dB/100ms
     */
    public boolean hasRapidFade()
    {
        return energySlope < -2.0f;
    }

    /**
     * Returns true if signal shows late start (low initial energy that rises).
     * Threshold: positive slope > 2 dB/100ms and weak preamble
     */
    public boolean hasLateStart()
    {
        return energySlope > 2.0f && hasWeakPreamble();
    }

    /**
     * Returns a diagnostic summary of the signal profile.
     */
    public String getDiagnosticSummary()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Avg=%.2f Peak/Avg=%.2f Preamble=%.2f (%.0f%%) Var=%.2f Slope=%.1fdB/100ms SNR=%.1fdB",
            avgEnergy, peakToAverage, preambleEnergy,
            avgEnergy > 0 ? (preambleEnergy / avgEnergy) * 100 : 0,
            energyVariance, energySlope, snrEstimate));

        if(hasWeakPreamble()) sb.append(" [WEAK_PREAMBLE]");
        if(hasHighVariance()) sb.append(" [HIGH_VARIANCE]");
        if(hasRapidFade()) sb.append(" [RAPID_FADE]");
        if(hasLateStart()) sb.append(" [LATE_START]");

        return sb.toString();
    }
}
