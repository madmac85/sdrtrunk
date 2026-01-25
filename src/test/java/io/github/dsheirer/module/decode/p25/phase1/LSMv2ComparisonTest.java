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

import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.P25P1DataUnitID;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Comparison test that plays a baseband recording through both LSM and LSM v2 decoders
 * and reports the decode statistics for each. Run with a path to a baseband WAV file.
 *
 * Usage: java LSMv2ComparisonTest <path-to-baseband.wav>
 */
public class LSMv2ComparisonTest
{
    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: LSMv2ComparisonTest <path-to-baseband.wav>");
            System.out.println("  Plays the baseband recording through both LSM and LSM v2 decoders");
            System.out.println("  and reports decode statistics for comparison.");
            return;
        }

        String filePath = args[0];
        File file = new File(filePath);

        if(!file.exists())
        {
            System.out.println("ERROR: File not found: " + filePath);
            return;
        }

        System.out.println("=== P25 LSM vs LSM v2 Comparison ===");
        System.out.println("File: " + file.getName());
        System.out.println();

        // Run LSM (original)
        System.out.println("--- Running LSM (original) ---");
        DecoderStats lsmStats = runDecoder(file, false);

        // Run LSM v2
        System.out.println();
        System.out.println("--- Running LSM v2 (cold-start improvements) ---");
        DecoderStats lsmV2Stats = runDecoder(file, true);

        // Print comparison
        System.out.println();
        System.out.println("=== COMPARISON RESULTS ===");
        System.out.println(String.format("%-25s %10s %10s %10s", "", "LSM", "LSM v2", "Delta"));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Valid Messages",
                lsmStats.validMessages, lsmV2Stats.validMessages,
                lsmV2Stats.validMessages - lsmStats.validMessages));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Total Messages",
                lsmStats.totalMessages, lsmV2Stats.totalMessages,
                lsmV2Stats.totalMessages - lsmStats.totalMessages));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Sync Losses",
                lsmStats.syncLosses, lsmV2Stats.syncLosses,
                lsmV2Stats.syncLosses - lsmStats.syncLosses));
        System.out.println(String.format("%-25s %10d %10d %+10d", "LDU Frames",
                lsmStats.lduCount, lsmV2Stats.lduCount,
                lsmV2Stats.lduCount - lsmStats.lduCount));
        System.out.println(String.format("%-25s %9.1fs %9.1fs %+9.1fs", "Audio (LDU×180ms)",
                lsmStats.lduCount * 0.18, lsmV2Stats.lduCount * 0.18,
                (lsmV2Stats.lduCount - lsmStats.lduCount) * 0.18));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Bit Errors",
                lsmStats.bitErrors, lsmV2Stats.bitErrors,
                lsmV2Stats.bitErrors - lsmStats.bitErrors));

        double lsmRate = lsmStats.totalMessages > 0 ?
                (double)lsmStats.validMessages / lsmStats.totalMessages * 100.0 : 0;
        double v2Rate = lsmV2Stats.totalMessages > 0 ?
                (double)lsmV2Stats.validMessages / lsmV2Stats.totalMessages * 100.0 : 0;
        System.out.println(String.format("%-25s %9.1f%% %9.1f%% %+9.1f%%", "Valid Rate",
                lsmRate, v2Rate, v2Rate - lsmRate));
        System.out.println();
        System.out.println("=== LSM v2 DIAGNOSTICS ===");
        System.out.println(sV2Diagnostics);

        // Analyze LDU gaps for missed transmission detection
        System.out.println();
        System.out.println("=== GAP ANALYSIS ===");
        // Gap threshold: 500ms (LDU pair is 360ms, allow margin for inter-transmission gaps)
        List<long[]> lsmGaps = lsmStats.findLduGaps(500);
        List<long[]> v2Gaps = lsmV2Stats.findLduGaps(500);
        System.out.println("LSM LDU gaps (>500ms): " + lsmGaps.size());
        System.out.println("v2  LDU gaps (>500ms): " + v2Gaps.size());

        // Find v2-specific regressions and gains
        int v2Regressions = findV2Regressions(lsmStats, lsmV2Stats);
        int v2Gains = findV2Gains(lsmStats, lsmV2Stats);
        System.out.println("v2-specific regressions (LSM decoded, v2 missed): " + v2Regressions);
        System.out.println("v2-specific gains (v2 decoded, LSM missed): " + v2Gains);
        System.out.println("Net v2 unique LDUs: " + (v2Gains - v2Regressions));

        // Report first few gaps for debugging
        if(!v2Gaps.isEmpty() && lsmV2Stats.lduCount < lsmStats.lduCount)
        {
            System.out.println();
            System.out.println("v2 gaps (first 5):");
            for(int i = 0; i < Math.min(5, v2Gaps.size()); i++)
            {
                long[] gap = v2Gaps.get(i);
                System.out.printf("  Gap at %dms, duration %dms%n", gap[0], gap[1]);
            }
        }
    }

    /**
     * Finds LDU timestamps that LSM decoded but v2 missed (v2-specific regressions).
     * Uses relative timestamps (normalized to first message) with 200ms tolerance.
     */
    private static int findV2Regressions(DecoderStats lsmStats, DecoderStats v2Stats)
    {
        if(lsmStats.lduTimestamps.isEmpty() || v2Stats.lduTimestamps.isEmpty())
        {
            return lsmStats.lduCount;
        }

        // Normalize timestamps to be relative to start of each decode session
        long lsmBase = lsmStats.allMessageTimestamps.isEmpty() ? 0 : lsmStats.allMessageTimestamps.get(0);
        long v2Base = v2Stats.allMessageTimestamps.isEmpty() ? 0 : v2Stats.allMessageTimestamps.get(0);

        List<Long> lsmRelative = lsmStats.lduTimestamps.stream().map(ts -> ts - lsmBase).toList();
        List<Long> v2Relative = v2Stats.lduTimestamps.stream().map(ts -> ts - v2Base).toList();

        int regressions = 0;
        final long TOLERANCE_MS = 200;

        for(Long lsmTs : lsmRelative)
        {
            boolean foundInV2 = false;
            for(Long v2Ts : v2Relative)
            {
                if(Math.abs(lsmTs - v2Ts) <= TOLERANCE_MS)
                {
                    foundInV2 = true;
                    break;
                }
            }
            if(!foundInV2)
            {
                regressions++;
            }
        }
        return regressions;
    }

    /**
     * Finds LDU timestamps that v2 decoded but LSM missed (v2-specific gains).
     */
    private static int findV2Gains(DecoderStats lsmStats, DecoderStats v2Stats)
    {
        if(lsmStats.lduTimestamps.isEmpty() || v2Stats.lduTimestamps.isEmpty())
        {
            return v2Stats.lduCount;
        }

        long lsmBase = lsmStats.allMessageTimestamps.isEmpty() ? 0 : lsmStats.allMessageTimestamps.get(0);
        long v2Base = v2Stats.allMessageTimestamps.isEmpty() ? 0 : v2Stats.allMessageTimestamps.get(0);

        List<Long> lsmRelative = lsmStats.lduTimestamps.stream().map(ts -> ts - lsmBase).toList();
        List<Long> v2Relative = v2Stats.lduTimestamps.stream().map(ts -> ts - v2Base).toList();

        int gains = 0;
        final long TOLERANCE_MS = 200;

        for(Long v2Ts : v2Relative)
        {
            boolean foundInLsm = false;
            for(Long lsmTs : lsmRelative)
            {
                if(Math.abs(v2Ts - lsmTs) <= TOLERANCE_MS)
                {
                    foundInLsm = true;
                    break;
                }
            }
            if(!foundInLsm)
            {
                gains++;
            }
        }
        return gains;
    }

    private static String sV2Diagnostics = "";

    private static DecoderStats runDecoder(File file, boolean useV2)
    {
        DecoderStats stats = new DecoderStats();

        Listener<IMessage> messageListener = iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                stats.totalMessages++;
                stats.allMessageTimestamps.add(message.getTimestamp());
                if(message.isValid())
                {
                    stats.validMessages++;
                    if(message.getMessage() != null)
                    {
                        stats.bitErrors += Math.max(message.getMessage().getCorrectedBitCount(), 0);
                    }
                    P25P1DataUnitID duid = message.getDUID();
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2)
                    {
                        stats.lduCount++;
                        stats.lduTimestamps.add(message.getTimestamp());
                    }
                }
            }
            else if(iMessage instanceof SyncLossMessage syncLoss)
            {
                stats.syncLosses++;
                stats.syncLossTimestamps.add(syncLoss.getTimestamp());
            }
        };

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            if(useV2)
            {
                P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
                decoder.setMessageListener(messageListener);
                decoder.start();

                source.setListener(iNativeBuffer -> {
                    Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });
                source.start();
                decoder.setSampleRate(source.getSampleRate());

                processFile(source);
                sV2Diagnostics = decoder.getDiagnostics();
                decoder.stop();
            }
            else
            {
                P25P1DecoderLSM decoder = new P25P1DecoderLSM();
                decoder.setMessageListener(messageListener);
                decoder.start();

                source.setListener(iNativeBuffer -> {
                    Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });
                source.start();
                decoder.setSampleRate(source.getSampleRate());

                processFile(source);
                decoder.stop();
            }
        }
        catch(IOException e)
        {
            System.out.println("ERROR: " + e.getMessage());
        }

        System.out.println("  Valid: " + stats.validMessages + " / Total: " + stats.totalMessages +
                " / Sync Losses: " + stats.syncLosses + " / Bit Errors: " + stats.bitErrors);

        return stats;
    }

    private static void processFile(ComplexWaveSource source) throws IOException
    {
        try
        {
            while(true)
            {
                source.next(2048, true);
            }
        }
        catch(Exception e)
        {
            // End of file
        }
    }

    private static class DecoderStats
    {
        int validMessages = 0;
        int totalMessages = 0;
        int syncLosses = 0;
        int bitErrors = 0;
        int lduCount = 0;

        // Timestamp tracking for correlation analysis
        List<Long> lduTimestamps = new ArrayList<>();
        List<Long> syncLossTimestamps = new ArrayList<>();
        List<Long> allMessageTimestamps = new ArrayList<>();

        /**
         * Finds LDU gaps - periods where LDUs were expected but not decoded.
         * @param maxGapMs maximum gap in ms before considering it a "missed" period
         * @return list of gap start times and durations
         */
        List<long[]> findLduGaps(long maxGapMs)
        {
            List<long[]> gaps = new ArrayList<>();
            if(lduTimestamps.size() < 2) return gaps;

            for(int i = 1; i < lduTimestamps.size(); i++)
            {
                long gap = lduTimestamps.get(i) - lduTimestamps.get(i - 1);
                // LDU1+LDU2 = 360ms, allow some margin
                if(gap > maxGapMs)
                {
                    gaps.add(new long[]{lduTimestamps.get(i - 1), gap});
                }
            }
            return gaps;
        }
    }
}
