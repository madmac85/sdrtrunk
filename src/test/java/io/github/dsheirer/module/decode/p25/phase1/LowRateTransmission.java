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
 * A transmission with low decode rate (below threshold) despite signal presence.
 * These are partial decode failures where the decoder acquired signal but lost it.
 */
public record LowRateTransmission(
    TransmissionDecodeResult result,
    double bestDecodeRate,   // Maximum decode rate between LSM and v2
    String diagnosticInfo    // Information about timing gaps
)
{
    /**
     * Default threshold for "low rate" classification (20%).
     */
    public static final double DEFAULT_THRESHOLD = 20.0;

    /**
     * The underlying transmission data.
     */
    public Transmission transmission()
    {
        return result.transmission();
    }

    /**
     * Transmission index in the file.
     */
    public int index()
    {
        return result.transmission().index();
    }

    /**
     * Duration in milliseconds.
     */
    public long durationMs()
    {
        return result.transmission().durationMs();
    }

    /**
     * Average signal energy.
     */
    public float avgEnergy()
    {
        return result.transmission().avgEnergy();
    }

    /**
     * Expected LDU count.
     */
    public int expectedLDUs()
    {
        return result.expectedLDUs();
    }

    /**
     * Best LDU count (max of LSM and v2).
     */
    public int bestLduCount()
    {
        return Math.max(result.lsmLduCount(), result.v2LduCount());
    }

    /**
     * Missing LDU count (expected - best).
     */
    public int missingLdus()
    {
        return expectedLDUs() - bestLduCount();
    }

    /**
     * LSM decode rate.
     */
    public double lsmDecodeRate()
    {
        return result.lsmDecodeRate();
    }

    /**
     * v2 decode rate.
     */
    public double v2DecodeRate()
    {
        return result.v2DecodeRate();
    }

    /**
     * Returns true if v2 performed better than LSM.
     */
    public boolean isV2Better()
    {
        return result.v2LduCount() > result.lsmLduCount();
    }

    /**
     * Returns a summary string for display.
     */
    public String toSummary()
    {
        String better = isV2Better() ? "v2" : (result.lsmLduCount() > result.v2LduCount() ? "LSM" : "tied");
        return String.format("TX#%d: %.1f%% decode rate (expected %d, got %d), %dms, best=%s [%s]",
            index(),
            bestDecodeRate,
            expectedLDUs(),
            bestLduCount(),
            durationMs(),
            better,
            diagnosticInfo);
    }

    /**
     * Creates a LowRateTransmission if the result qualifies (below threshold).
     *
     * @param result the transmission decode result
     * @param threshold the decode rate threshold (percentage)
     * @return LowRateTransmission if below threshold, null otherwise
     */
    public static LowRateTransmission createIfQualifies(TransmissionDecodeResult result, double threshold)
    {
        // Skip missed transmissions (analyzed separately)
        if(result.lsmLduCount() == 0 && result.v2LduCount() == 0)
        {
            return null;
        }

        double lsmRate = result.lsmDecodeRate();
        double v2Rate = result.v2DecodeRate();
        double bestRate = Math.max(lsmRate, v2Rate);

        if(bestRate < threshold)
        {
            String diagnostic = generateDiagnostic(result);
            return new LowRateTransmission(result, bestRate, diagnostic);
        }

        return null;
    }

    /**
     * Generates diagnostic information about the low decode rate.
     */
    private static String generateDiagnostic(TransmissionDecodeResult result)
    {
        int expected = result.expectedLDUs();
        int lsmCount = result.lsmLduCount();
        int v2Count = result.v2LduCount();

        StringBuilder sb = new StringBuilder();

        // Describe the decode pattern
        if(lsmCount == 0 && v2Count > 0)
        {
            sb.append("v2-only decode");
        }
        else if(v2Count == 0 && lsmCount > 0)
        {
            sb.append("LSM-only decode");
        }
        else if(lsmCount == v2Count)
        {
            sb.append("equal decode");
        }
        else if(v2Count > lsmCount)
        {
            sb.append("v2 better by ").append(v2Count - lsmCount);
        }
        else
        {
            sb.append("LSM better by ").append(lsmCount - v2Count);
        }

        // Note expected vs actual
        int best = Math.max(lsmCount, v2Count);
        int missing = expected - best;
        if(missing > 0)
        {
            sb.append(", missing ").append(missing).append(" of ").append(expected).append(" LDUs");
        }

        return sb.toString();
    }
}
