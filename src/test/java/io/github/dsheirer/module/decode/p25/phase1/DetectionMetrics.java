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

import java.util.List;

/**
 * Aggregate statistics for transmission detection vs decode performance.
 * Compares expected transmissions (from signal detection) against actual decode results.
 */
public class DetectionMetrics {
    // Expected counts from signal detection
    private int totalExpectedTransmissions = 0;
    private int totalExpectedLDUs = 0;

    // Actual decode counts
    private int lsmDecodedTransmissions = 0;
    private int lsmDecodedLDUs = 0;
    private int v2DecodedTransmissions = 0;
    private int v2DecodedLDUs = 0;

    // Status breakdown
    private int bothDecodedCount = 0;
    private int lsmOnlyCount = 0;
    private int v2OnlyCount = 0;
    private int missedCount = 0;

    // Error metrics
    private int lsmBitErrors = 0;
    private int v2BitErrors = 0;

    /**
     * Creates empty metrics. Use accumulate() to add results.
     */
    public DetectionMetrics() {
    }

    /**
     * Creates metrics from a list of transmission decode results.
     */
    public DetectionMetrics(List<TransmissionDecodeResult> results) {
        for(TransmissionDecodeResult result : results) {
            accumulate(result);
        }
    }

    /**
     * Accumulates a single transmission result into the metrics.
     */
    public void accumulate(TransmissionDecodeResult result) {
        totalExpectedTransmissions++;
        totalExpectedLDUs += result.expectedLDUs();

        if(result.lsmLduCount() > 0) {
            lsmDecodedTransmissions++;
        }
        lsmDecodedLDUs += result.lsmLduCount();
        lsmBitErrors += result.lsmBitErrors();

        if(result.v2LduCount() > 0) {
            v2DecodedTransmissions++;
        }
        v2DecodedLDUs += result.v2LduCount();
        v2BitErrors += result.v2BitErrors();

        switch(result.status()) {
            case BOTH_DECODED -> bothDecodedCount++;
            case LSM_ONLY -> lsmOnlyCount++;
            case V2_ONLY -> v2OnlyCount++;
            case MISSED -> missedCount++;
        }
    }

    /**
     * Merges another DetectionMetrics into this one (for multi-file aggregation).
     */
    public void merge(DetectionMetrics other) {
        totalExpectedTransmissions += other.totalExpectedTransmissions;
        totalExpectedLDUs += other.totalExpectedLDUs;
        lsmDecodedTransmissions += other.lsmDecodedTransmissions;
        lsmDecodedLDUs += other.lsmDecodedLDUs;
        v2DecodedTransmissions += other.v2DecodedTransmissions;
        v2DecodedLDUs += other.v2DecodedLDUs;
        bothDecodedCount += other.bothDecodedCount;
        lsmOnlyCount += other.lsmOnlyCount;
        v2OnlyCount += other.v2OnlyCount;
        missedCount += other.missedCount;
        lsmBitErrors += other.lsmBitErrors;
        v2BitErrors += other.v2BitErrors;
    }

    // Getters
    public int getTotalExpectedTransmissions() { return totalExpectedTransmissions; }
    public int getTotalExpectedLDUs() { return totalExpectedLDUs; }
    public int getLsmDecodedTransmissions() { return lsmDecodedTransmissions; }
    public int getLsmDecodedLDUs() { return lsmDecodedLDUs; }
    public int getV2DecodedTransmissions() { return v2DecodedTransmissions; }
    public int getV2DecodedLDUs() { return v2DecodedLDUs; }
    public int getBothDecodedCount() { return bothDecodedCount; }
    public int getLsmOnlyCount() { return lsmOnlyCount; }
    public int getV2OnlyCount() { return v2OnlyCount; }
    public int getMissedCount() { return missedCount; }
    public int getLsmBitErrors() { return lsmBitErrors; }
    public int getV2BitErrors() { return v2BitErrors; }

    // Calculated metrics
    public double lsmTransmissionDecodeRate() {
        return totalExpectedTransmissions > 0 ?
            (double)lsmDecodedTransmissions / totalExpectedTransmissions * 100.0 : 0;
    }

    public double v2TransmissionDecodeRate() {
        return totalExpectedTransmissions > 0 ?
            (double)v2DecodedTransmissions / totalExpectedTransmissions * 100.0 : 0;
    }

    public double lsmLduDecodeRate() {
        return totalExpectedLDUs > 0 ?
            (double)lsmDecodedLDUs / totalExpectedLDUs * 100.0 : 0;
    }

    public double v2LduDecodeRate() {
        return totalExpectedLDUs > 0 ?
            (double)v2DecodedLDUs / totalExpectedLDUs * 100.0 : 0;
    }

    public int v2LduImprovement() {
        return v2DecodedLDUs - lsmDecodedLDUs;
    }

    public double v2LduImprovementPercent() {
        return lsmDecodedLDUs > 0 ?
            (double)(v2DecodedLDUs - lsmDecodedLDUs) / lsmDecodedLDUs * 100.0 : 0;
    }

    public int v2TransmissionImprovement() {
        return v2DecodedTransmissions - lsmDecodedTransmissions;
    }

    /**
     * Generates a formatted summary report.
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRANSMISSION DETECTION METRICS ===\n");
        sb.append(String.format("Expected Transmissions: %d\n", totalExpectedTransmissions));
        sb.append(String.format("Expected LDUs:          %d\n", totalExpectedLDUs));
        sb.append("\n");

        sb.append(String.format("%-20s %10s %10s %10s\n", "", "LSM", "v2", "Delta"));
        sb.append("-".repeat(52)).append("\n");
        sb.append(String.format("%-20s %10d %10d %+10d\n", "Decoded TX:",
            lsmDecodedTransmissions, v2DecodedTransmissions, v2TransmissionImprovement()));
        sb.append(String.format("%-20s %10d %10d %+10d\n", "Decoded LDUs:",
            lsmDecodedLDUs, v2DecodedLDUs, v2LduImprovement()));
        sb.append(String.format("%-20s %9.1f%% %9.1f%% %+9.1f%%\n", "LDU Decode Rate:",
            lsmLduDecodeRate(), v2LduDecodeRate(), v2LduDecodeRate() - lsmLduDecodeRate()));
        sb.append("\n");

        sb.append("Transmission Status:\n");
        sb.append(String.format("  Both decoded:     %4d (%5.1f%%)\n",
            bothDecodedCount, percentage(bothDecodedCount)));
        sb.append(String.format("  v2 only:          %4d (%5.1f%%)\n",
            v2OnlyCount, percentage(v2OnlyCount)));
        sb.append(String.format("  LSM only:         %4d (%5.1f%%)\n",
            lsmOnlyCount, percentage(lsmOnlyCount)));
        sb.append(String.format("  Missed (neither): %4d (%5.1f%%)\n",
            missedCount, percentage(missedCount)));

        if(v2LduImprovement() > 0) {
            sb.append("\n");
            sb.append(String.format("v2 Improvement: +%d LDUs (+%.1f%%)\n",
                v2LduImprovement(), v2LduImprovementPercent()));
        }

        return sb.toString();
    }

    private double percentage(int count) {
        return totalExpectedTransmissions > 0 ?
            (double)count / totalExpectedTransmissions * 100.0 : 0;
    }
}
