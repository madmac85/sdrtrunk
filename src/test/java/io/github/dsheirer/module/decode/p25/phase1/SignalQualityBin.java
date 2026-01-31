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
 * Aggregates transmission statistics for a signal quality tier.
 * Used for correlation analysis between signal strength and decode success.
 */
public class SignalQualityBin
{
    private final int binIndex;
    private final float minEnergy;
    private final float maxEnergy;

    private int totalTransmissions = 0;
    private int lsmDecoded = 0;
    private int v2Decoded = 0;
    private int bothDecoded = 0;
    private int missed = 0;

    private int totalExpectedLDUs = 0;
    private int lsmLDUs = 0;
    private int v2LDUs = 0;

    /**
     * Creates a signal quality bin for the specified energy range.
     *
     * @param binIndex the bin number (1-based for display)
     * @param minEnergy minimum energy value (inclusive)
     * @param maxEnergy maximum energy value (exclusive, except for last bin)
     */
    public SignalQualityBin(int binIndex, float minEnergy, float maxEnergy)
    {
        this.binIndex = binIndex;
        this.minEnergy = minEnergy;
        this.maxEnergy = maxEnergy;
    }

    /**
     * Checks if a transmission's average energy falls within this bin.
     */
    public boolean contains(float energy)
    {
        return energy >= minEnergy && energy < maxEnergy;
    }

    /**
     * Adds a transmission result to this bin's statistics.
     */
    public void add(TransmissionDecodeResult result)
    {
        totalTransmissions++;
        totalExpectedLDUs += result.expectedLDUs();
        lsmLDUs += result.lsmLduCount();
        v2LDUs += result.v2LduCount();

        boolean lsmOk = result.lsmLduCount() > 0;
        boolean v2Ok = result.v2LduCount() > 0;

        if(lsmOk) lsmDecoded++;
        if(v2Ok) v2Decoded++;
        if(lsmOk && v2Ok) bothDecoded++;
        if(!lsmOk && !v2Ok) missed++;
    }

    // Getters
    public int getBinIndex() { return binIndex; }
    public float getMinEnergy() { return minEnergy; }
    public float getMaxEnergy() { return maxEnergy; }
    public int getTotalTransmissions() { return totalTransmissions; }
    public int getLsmDecoded() { return lsmDecoded; }
    public int getV2Decoded() { return v2Decoded; }
    public int getBothDecoded() { return bothDecoded; }
    public int getMissed() { return missed; }
    public int getTotalExpectedLDUs() { return totalExpectedLDUs; }
    public int getLsmLDUs() { return lsmLDUs; }
    public int getV2LDUs() { return v2LDUs; }

    /**
     * LSM transmission decode rate (percentage of transmissions with at least 1 LDU).
     */
    public double lsmTransmissionDecodeRate()
    {
        return totalTransmissions > 0 ? (double) lsmDecoded / totalTransmissions * 100.0 : 0;
    }

    /**
     * v2 transmission decode rate (percentage of transmissions with at least 1 LDU).
     */
    public double v2TransmissionDecodeRate()
    {
        return totalTransmissions > 0 ? (double) v2Decoded / totalTransmissions * 100.0 : 0;
    }

    /**
     * LSM LDU decode rate (percentage of expected LDUs decoded).
     */
    public double lsmLduDecodeRate()
    {
        return totalExpectedLDUs > 0 ? (double) lsmLDUs / totalExpectedLDUs * 100.0 : 0;
    }

    /**
     * v2 LDU decode rate (percentage of expected LDUs decoded).
     */
    public double v2LduDecodeRate()
    {
        return totalExpectedLDUs > 0 ? (double) v2LDUs / totalExpectedLDUs * 100.0 : 0;
    }

    /**
     * Percentage of transmissions missed by both decoders.
     */
    public double missedRate()
    {
        return totalTransmissions > 0 ? (double) missed / totalTransmissions * 100.0 : 0;
    }

    /**
     * Potential LDU gain if all missed transmissions were decoded.
     */
    public int potentialLduGain()
    {
        // Estimate LDUs from missed transmissions
        // This is approximate since we don't track expected LDUs per-missed
        return totalExpectedLDUs > 0 ?
            (int)((double) missed / totalTransmissions * totalExpectedLDUs) : 0;
    }

    /**
     * Returns formatted energy range string.
     */
    public String getEnergyRangeString()
    {
        return String.format("%.4f-%.4f", minEnergy, maxEnergy);
    }

    /**
     * Creates bins covering the observed energy range.
     *
     * @param results all transmission results to analyze
     * @param binCount number of bins to create
     * @return list of bins covering the energy range
     */
    public static List<SignalQualityBin> createBins(List<TransmissionDecodeResult> results, int binCount)
    {
        if(results.isEmpty())
        {
            return List.of();
        }

        // Find energy range
        float minEnergy = Float.MAX_VALUE;
        float maxEnergy = Float.MIN_VALUE;

        for(TransmissionDecodeResult result : results)
        {
            float energy = result.transmission().avgEnergy();
            minEnergy = Math.min(minEnergy, energy);
            maxEnergy = Math.max(maxEnergy, energy);
        }

        // Create bins
        float binWidth = (maxEnergy - minEnergy) / binCount;
        List<SignalQualityBin> bins = new ArrayList<>();

        for(int i = 0; i < binCount; i++)
        {
            float binMin = minEnergy + (i * binWidth);
            float binMax = (i == binCount - 1) ?
                maxEnergy + 0.0001f :  // Include max value in last bin
                minEnergy + ((i + 1) * binWidth);
            bins.add(new SignalQualityBin(i + 1, binMin, binMax));
        }

        // Populate bins
        for(TransmissionDecodeResult result : results)
        {
            float energy = result.transmission().avgEnergy();
            for(SignalQualityBin bin : bins)
            {
                if(bin.contains(energy))
                {
                    bin.add(result);
                    break;
                }
            }
        }

        return bins;
    }

    /**
     * Formats the bin as a table row.
     */
    public String toTableRow()
    {
        return String.format("%3d   %s   %5d   %5.1f%%   %5.1f%%   %5d (%5.1f%%)",
            binIndex,
            getEnergyRangeString(),
            totalTransmissions,
            lsmTransmissionDecodeRate(),
            v2TransmissionDecodeRate(),
            missed,
            missedRate());
    }
}
