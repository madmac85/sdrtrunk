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

/**
 * Represents a detected transmission from energy analysis of baseband I/Q samples.
 * A transmission is a period of RF activity bounded by silence on both ends.
 */
public record Transmission(
    int index,           // Transmission number in file (1-based)
    long startMs,        // Start timestamp in milliseconds from file start
    long endMs,          // End timestamp in milliseconds from file start
    float peakEnergy,    // Peak signal energy observed during transmission
    float avgEnergy,     // Average signal energy during transmission
    float preambleEnergy,// Average energy during first 100ms (preamble window)
    float energyVariance,// Standard deviation of energy over transmission
    boolean isComplete   // True if transmission has clear start and end, false if cut off at recording boundary
) {
    /**
     * Duration of the transmission in milliseconds.
     */
    public long durationMs()
    {
        return endMs - startMs;
    }

    /**
     * Expected number of LDU frames based on duration.
     * LDU frame duration is 180ms per P25 specification.
     * Returns minimum of 1 to avoid zero expectations for very short transmissions.
     */
    public int expectedLDUs()
    {
        return Math.max(1, (int)(durationMs() / 180));
    }

    /**
     * Returns a brief summary string for display.
     */
    public String toSummary()
    {
        return String.format("TX#%d [%d-%d ms, %d ms, %d expected LDUs%s]",
            index, startMs, endMs, durationMs(), expectedLDUs(),
            isComplete ? "" : ", incomplete");
    }

    /**
     * Returns the ratio of preamble energy to average energy.
     * Values below 0.7 indicate weak preamble condition.
     */
    public float preambleRatio()
    {
        return avgEnergy > 0 ? preambleEnergy / avgEnergy : 0;
    }

    /**
     * Returns true if the preamble energy is significantly weaker than the average.
     * Threshold: preamble < 70% of average energy.
     */
    public boolean hasWeakPreamble()
    {
        return avgEnergy > 0 && preambleEnergy < avgEnergy * 0.7f;
    }
}
