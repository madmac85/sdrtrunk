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
 * Scores transmission decode quality by correlating decoded messages with detected transmission boundaries.
 */
public class TransmissionScorer
{
    // Tolerance for matching timestamps near transmission boundaries
    private static final long BOUNDARY_TOLERANCE_MS = 150;

    // Tolerance for matching messages within transmission
    private static final long MATCH_TOLERANCE_MS = 100;

    /**
     * Scores a single transmission's decode quality for both LSM and LSM v2 decoders.
     *
     * @param tx the transmission to score
     * @param lsmStats decoder stats from LSM decoder
     * @param v2Stats decoder stats from LSM v2 decoder
     * @param baseTimestamp the base timestamp for normalizing decoder timestamps
     * @return score containing metrics for this transmission
     */
    public TransmissionScore score(
        Transmission tx,
        LSMv2ComparisonTest.DecoderStats lsmStats,
        LSMv2ComparisonTest.DecoderStats v2Stats,
        long baseTimestamp)
    {
        // Count LDUs within transmission boundaries
        int lsmLduCount = countMessagesInRange(
            tx.startMs(), tx.endMs(),
            lsmStats.lduTimestamps, baseTimestamp);

        int v2LduCount = countMessagesInRange(
            tx.startMs(), tx.endMs(),
            v2Stats.lduTimestamps, baseTimestamp);

        // Check for HDU near transmission start
        boolean lsmHasHDU = hasMessageNear(
            tx.startMs(), BOUNDARY_TOLERANCE_MS,
            lsmStats.hduTimestamps, baseTimestamp);

        boolean v2HasHDU = hasMessageNear(
            tx.startMs(), BOUNDARY_TOLERANCE_MS,
            v2Stats.hduTimestamps, baseTimestamp);

        // Check for TDU/TDULC near transmission end
        boolean lsmHasTDU = hasMessageNear(
            tx.endMs(), BOUNDARY_TOLERANCE_MS,
            lsmStats.tduTimestamps, baseTimestamp);

        boolean v2HasTDU = hasMessageNear(
            tx.endMs(), BOUNDARY_TOLERANCE_MS,
            v2Stats.tduTimestamps, baseTimestamp);

        return new TransmissionScore(
            tx,
            lsmLduCount,
            v2LduCount,
            lsmHasHDU,
            v2HasHDU,
            lsmHasTDU,
            v2HasTDU
        );
    }

    /**
     * Counts messages that fall within the specified time range.
     *
     * @param startMs transmission start time
     * @param endMs transmission end time
     * @param timestamps list of message timestamps
     * @param baseTimestamp base time for normalizing timestamps
     * @return count of messages within range
     */
    private int countMessagesInRange(long startMs, long endMs, List<Long> timestamps, long baseTimestamp)
    {
        int count = 0;
        for(Long ts : timestamps)
        {
            long normalizedTs = ts - baseTimestamp;
            // Add tolerance on both ends to catch messages at boundaries
            if(normalizedTs >= (startMs - MATCH_TOLERANCE_MS) &&
               normalizedTs <= (endMs + MATCH_TOLERANCE_MS))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if any message is within tolerance of a target timestamp.
     *
     * @param targetMs target timestamp
     * @param toleranceMs tolerance window
     * @param timestamps list of message timestamps
     * @param baseTimestamp base time for normalizing
     * @return true if at least one message is within tolerance
     */
    private boolean hasMessageNear(long targetMs, long toleranceMs, List<Long> timestamps, long baseTimestamp)
    {
        for(Long ts : timestamps)
        {
            long normalizedTs = ts - baseTimestamp;
            if(Math.abs(normalizedTs - targetMs) <= toleranceMs)
            {
                return true;
            }
        }
        return false;
    }
}
