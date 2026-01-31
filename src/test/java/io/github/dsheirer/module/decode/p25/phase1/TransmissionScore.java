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
 * Holds decode quality metrics for a single transmission, comparing LSM and LSM v2 decoder performance.
 */
public record TransmissionScore(
    Transmission transmission,  // The transmission being scored
    int lsmLduCount,           // Number of LDUs decoded by LSM
    int v2LduCount,            // Number of LDUs decoded by LSM v2
    boolean lsmHasHDU,         // True if LSM decoded HDU at start
    boolean v2HasHDU,          // True if v2 decoded HDU at start
    boolean lsmHasTDU,         // True if LSM decoded TDU/TDULC at end
    boolean v2HasTDU,          // True if v2 decoded TDU/TDULC at end
    // Error metrics
    int lsmBitErrors,          // Bit errors corrected by LSM
    int v2BitErrors,           // Bit errors corrected by v2
    int lsmInvalidMessages,    // Invalid messages in LSM
    int v2InvalidMessages      // Invalid messages in v2
) {
    /**
     * LSM decoder score as percentage of expected LDUs decoded.
     */
    public double lsmScore()
    {
        int expected = transmission.expectedLDUs();
        return expected > 0 ? (double)lsmLduCount / expected * 100.0 : 0.0;
    }

    /**
     * LSM v2 decoder score as percentage of expected LDUs decoded.
     */
    public double v2Score()
    {
        int expected = transmission.expectedLDUs();
        return expected > 0 ? (double)v2LduCount / expected * 100.0 : 0.0;
    }

    /**
     * Returns true if v2 decoded fewer LDUs than LSM (regression).
     */
    public boolean isV2Regression()
    {
        return v2LduCount < lsmLduCount;
    }

    /**
     * Returns true if v2 decoded more LDUs than LSM (improvement).
     */
    public boolean isV2Improvement()
    {
        return v2LduCount > lsmLduCount;
    }

    /**
     * LDU count difference (v2 - LSM). Positive means v2 is better.
     */
    public int delta()
    {
        return v2LduCount - lsmLduCount;
    }

    /**
     * Returns true if the transmission has complete framing (both HDU and TDU).
     */
    public boolean hasCompleteFraming()
    {
        return v2HasHDU && v2HasTDU;
    }

    /**
     * Returns a compact status string for the transmission flags.
     */
    public String flagsString()
    {
        StringBuilder sb = new StringBuilder();
        if(isV2Regression()) sb.append("REG ");
        if(!v2HasHDU) sb.append("noHDU ");
        if(!v2HasTDU) sb.append("noTDU ");
        if(!transmission.isComplete()) sb.append("TRUNC ");
        return sb.toString().trim();
    }

    /**
     * Estimated bit error rate for LSM decoder.
     * Based on expected LDUs * 1568 bits per LDU.
     */
    public double lsmBER()
    {
        int expectedBits = transmission.expectedLDUs() * 1568;
        return expectedBits > 0 ? (double)lsmBitErrors / expectedBits * 100.0 : 0;
    }

    /**
     * Estimated bit error rate for LSM v2 decoder.
     * Based on expected LDUs * 1568 bits per LDU.
     */
    public double v2BER()
    {
        int expectedBits = transmission.expectedLDUs() * 1568;
        return expectedBits > 0 ? (double)v2BitErrors / expectedBits * 100.0 : 0;
    }

    /**
     * Bit error improvement (positive means v2 has fewer errors).
     */
    public int bitErrorDelta()
    {
        return lsmBitErrors - v2BitErrors;
    }

    /**
     * Invalid message improvement (positive means v2 has fewer invalid messages).
     */
    public int invalidMessageDelta()
    {
        return lsmInvalidMessages - v2InvalidMessages;
    }
}
