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
 * Combines expected transmission (from signal detection) with actual decode results
 * from both LSM and LSM v2 decoders for comparison analysis.
 */
public record TransmissionDecodeResult(
    Transmission transmission,  // Expected transmission from signal detection
    int lsmLduCount,           // Actual LDUs decoded by LSM
    int v2LduCount,            // Actual LDUs decoded by LSM v2
    int lsmBitErrors,          // Bit errors corrected by LSM
    int v2BitErrors,           // Bit errors corrected by v2
    boolean lsmHasHDU,         // LSM decoded HDU at start
    boolean v2HasHDU,          // v2 decoded HDU at start
    boolean lsmHasTDU,         // LSM decoded TDU at end
    boolean v2HasTDU           // v2 decoded TDU at end
) {
    /**
     * Decode status classification.
     */
    public enum DecodeStatus {
        BOTH_DECODED,  // Both decoders recovered LDUs
        LSM_ONLY,      // Only LSM recovered LDUs
        V2_ONLY,       // Only v2 recovered LDUs
        MISSED         // Neither decoder recovered any LDUs
    }

    /**
     * Expected LDU count based on transmission duration.
     */
    public int expectedLDUs() {
        return transmission.expectedLDUs();
    }

    /**
     * LSM decode rate as percentage of expected LDUs.
     */
    public double lsmDecodeRate() {
        int expected = expectedLDUs();
        return expected > 0 ? (double)lsmLduCount / expected * 100.0 : 0.0;
    }

    /**
     * v2 decode rate as percentage of expected LDUs.
     */
    public double v2DecodeRate() {
        int expected = expectedLDUs();
        return expected > 0 ? (double)v2LduCount / expected * 100.0 : 0.0;
    }

    /**
     * Decode status classification for this transmission.
     */
    public DecodeStatus status() {
        boolean lsmDecoded = lsmLduCount > 0;
        boolean v2Decoded = v2LduCount > 0;

        if(lsmDecoded && v2Decoded) {
            return DecodeStatus.BOTH_DECODED;
        } else if(lsmDecoded) {
            return DecodeStatus.LSM_ONLY;
        } else if(v2Decoded) {
            return DecodeStatus.V2_ONLY;
        } else {
            return DecodeStatus.MISSED;
        }
    }

    /**
     * Returns true if v2 decoded more LDUs than LSM.
     */
    public boolean isV2Better() {
        return v2LduCount > lsmLduCount;
    }

    /**
     * Returns true if LSM decoded more LDUs than v2.
     */
    public boolean isLsmBetter() {
        return lsmLduCount > v2LduCount;
    }

    /**
     * LDU count difference (v2 - LSM). Positive means v2 is better.
     */
    public int lduDelta() {
        return v2LduCount - lsmLduCount;
    }

    /**
     * Normalized signal quality from 0 to 1 based on average energy.
     * Higher values indicate stronger signals.
     */
    public float signalQuality() {
        // Normalize based on typical energy ranges (0.001 to 0.1)
        float avg = transmission.avgEnergy();
        return Math.min(1.0f, avg * 10.0f);
    }

    /**
     * Returns a summary string for display.
     */
    public String toSummary() {
        return String.format("TX#%d: Expected=%d, LSM=%d (%.1f%%), v2=%d (%.1f%%), Status=%s",
            transmission.index(),
            expectedLDUs(),
            lsmLduCount, lsmDecodeRate(),
            v2LduCount, v2DecodeRate(),
            status());
    }
}
