/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 3-way decoder comparison test for P25 Phase 1 decoders.
 * Runs C4FM, LSM, and LSM v2 decoders against the same baseband recordings
 * and reports comparative decode statistics.
 *
 * Usage:
 *   Single file: java DerryDecoderComparisonTest <path-to-baseband.wav> [nac]
 *   Directory:    java DerryDecoderComparisonTest --dir <path-to-directory> [nac]
 */
public class DerryDecoderComparisonTest
{
    private static int sConfiguredNAC = 0;

    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: DerryDecoderComparisonTest <path-to-baseband.wav> [nac]");
            System.out.println("       DerryDecoderComparisonTest --dir <directory> [nac]");
            System.out.println();
            System.out.println("  Runs C4FM, LSM, and LSM v2 decoders against baseband recordings");
            System.out.println("  and reports comparative decode statistics.");
            System.out.println("  Optional: specify known NAC value (0-4095) for improved error correction");
            return;
        }

        // Parse arguments
        List<File> files = new ArrayList<>();
        boolean dirMode = false;
        int argIndex = 0;

        if("--dir".equals(args[0]))
        {
            dirMode = true;
            argIndex = 1;
            if(args.length < 2)
            {
                System.out.println("ERROR: --dir requires a directory path");
                return;
            }
            File dir = new File(args[1]);
            if(!dir.isDirectory())
            {
                System.out.println("ERROR: Not a directory: " + args[1]);
                return;
            }
            File[] wavFiles = dir.listFiles((d, name) -> name.endsWith("_baseband.wav"));
            if(wavFiles == null || wavFiles.length == 0)
            {
                System.out.println("ERROR: No baseband WAV files found in: " + args[1]);
                return;
            }
            java.util.Arrays.sort(wavFiles, (a, b) -> a.getName().compareTo(b.getName()));
            for(File f : wavFiles)
            {
                files.add(f);
            }
            argIndex = 2;
        }
        else
        {
            files.add(new File(args[0]));
            argIndex = 1;
        }

        // Parse optional NAC argument
        if(args.length > argIndex)
        {
            try
            {
                sConfiguredNAC = Integer.parseInt(args[argIndex]);
                if(sConfiguredNAC < 0 || sConfiguredNAC > 4095)
                {
                    System.out.println("WARNING: NAC must be 0-4095, ignoring: " + sConfiguredNAC);
                    sConfiguredNAC = 0;
                }
            }
            catch(NumberFormatException e)
            {
                System.out.println("WARNING: Invalid NAC value, ignoring: " + args[argIndex]);
            }
        }

        // Verify all files exist
        for(File f : files)
        {
            if(!f.exists())
            {
                System.out.println("ERROR: File not found: " + f.getAbsolutePath());
                return;
            }
        }

        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              P25 Phase 1 — 3-Way Decoder Comparison                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        if(sConfiguredNAC > 0)
        {
            System.out.println("Configured NAC: " + sConfiguredNAC);
        }
        System.out.println("Files to process: " + files.size());
        System.out.println();

        // Aggregate stats across all files
        DecoderStats totalC4fm = new DecoderStats();
        DecoderStats totalLsm = new DecoderStats();
        DecoderStats totalLsmV2 = new DecoderStats();
        String lastLsmV2Diagnostics = "";

        for(File file : files)
        {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("FILE: " + file.getName());
            long fileSizeKB = file.length() / 1024;
            // Baseband WAV: 4 bytes/sample * 2 channels, 48000 sample rate → duration
            double fileDurationSec = (file.length() - 44.0) / (4 * 2 * 48000);
            System.out.printf("  Size: %,d KB | Duration: %.1f sec%n", fileSizeKB, fileDurationSec);
            System.out.println();

            // Run all three decoders
            System.out.println("  [1/3] Running C4FM decoder...");
            DecoderStats c4fmStats = runC4FMDecoder(file);
            printBrief("C4FM  ", c4fmStats);

            System.out.println("  [2/3] Running LSM decoder...");
            DecoderStats lsmStats = runLSMDecoder(file);
            printBrief("LSM   ", lsmStats);

            System.out.println("  [3/3] Running LSM v2 decoder...");
            String[] lsmV2Diag = new String[1];
            DecoderStats lsmV2Stats = runLSMv2Decoder(file, lsmV2Diag);
            printBrief("LSMv2 ", lsmV2Stats);
            lastLsmV2Diagnostics = lsmV2Diag[0];

            // Print per-file comparison
            System.out.println();
            printComparison(file.getName(), c4fmStats, lsmStats, lsmV2Stats);

            // Accumulate totals
            totalC4fm.accumulate(c4fmStats);
            totalLsm.accumulate(lsmStats);
            totalLsmV2.accumulate(lsmV2Stats);
        }

        // Print aggregate results if multiple files
        if(files.size() > 1)
        {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                         AGGREGATE RESULTS                                  ║");
            System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
            printComparison("ALL FILES", totalC4fm, totalLsm, totalLsmV2);
        }

        // Print v2 diagnostics from last file
        if(lastLsmV2Diagnostics != null && !lastLsmV2Diagnostics.isEmpty())
        {
            System.out.println();
            System.out.println("=== LSM v2 DIAGNOSTICS (last file) ===");
            System.out.println(lastLsmV2Diagnostics);
        }

        // Print cross-decoder gap analysis
        System.out.println();
        System.out.println("=== CROSS-DECODER GAP ANALYSIS (aggregate) ===");
        List<long[]> c4fmGaps = totalC4fm.findLduGaps(500);
        List<long[]> lsmGaps = totalLsm.findLduGaps(500);
        List<long[]> lsmV2Gaps = totalLsmV2.findLduGaps(500);
        System.out.println("C4FM   LDU gaps (>500ms): " + c4fmGaps.size());
        System.out.println("LSM    LDU gaps (>500ms): " + lsmGaps.size());
        System.out.println("LSMv2  LDU gaps (>500ms): " + lsmV2Gaps.size());

        // Cross-decoder regressions/gains
        System.out.println();
        System.out.println("=== CROSS-DECODER UNIQUE LDU ANALYSIS ===");
        int c4fmVsLsm = findUniqueCount(totalC4fm, totalLsm);
        int lsmVsC4fm = findUniqueCount(totalLsm, totalC4fm);
        int c4fmVsLsmV2 = findUniqueCount(totalC4fm, totalLsmV2);
        int lsmV2VsC4fm = findUniqueCount(totalLsmV2, totalC4fm);
        int lsmVsLsmV2 = findUniqueCount(totalLsm, totalLsmV2);
        int lsmV2VsLsm = findUniqueCount(totalLsmV2, totalLsm);

        System.out.printf("C4FM   unique vs LSM:    %d | LSM    unique vs C4FM:   %d%n", c4fmVsLsm, lsmVsC4fm);
        System.out.printf("C4FM   unique vs LSMv2:  %d | LSMv2  unique vs C4FM:   %d%n", c4fmVsLsmV2, lsmV2VsC4fm);
        System.out.printf("LSM    unique vs LSMv2:  %d | LSMv2  unique vs LSM:    %d%n", lsmVsLsmV2, lsmV2VsLsm);

        // Winner determination
        System.out.println();
        System.out.println("=== DECODER RECOMMENDATION ===");
        determineWinner(totalC4fm, totalLsm, totalLsmV2);
    }

    private static void printBrief(String name, DecoderStats stats)
    {
        System.out.printf("  %s: Valid=%d Total=%d LDU=%d SyncLoss=%d BitErr=%d%n",
                name, stats.validMessages, stats.totalMessages, stats.lduCount,
                stats.syncLosses, stats.bitErrors);
    }

    private static void printComparison(String label, DecoderStats c4fm,
                                        DecoderStats lsm, DecoderStats lsmV2)
    {
        System.out.println("  ┌────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ COMPARISON: " + padRight(label, 50) + "│");
        System.out.println("  ├─────────────────────────┬──────────┬──────────┬───────────────┤");
        System.out.println("  │ Metric                  │   C4FM   │   LSM    │    LSM v2     │");
        System.out.println("  ├─────────────────────────┼──────────┼──────────┼───────────────┤");
        System.out.printf("  │ Valid Messages           │ %8d │ %8d │ %13d │%n",
                c4fm.validMessages, lsm.validMessages, lsmV2.validMessages);
        System.out.printf("  │ Total Messages           │ %8d │ %8d │ %13d │%n",
                c4fm.totalMessages, lsm.totalMessages, lsmV2.totalMessages);
        System.out.printf("  │ Invalid Messages         │ %8d │ %8d │ %13d │%n",
                c4fm.invalidMessages, lsm.invalidMessages, lsmV2.invalidMessages);
        System.out.printf("  │ Sync Losses              │ %8d │ %8d │ %13d │%n",
                c4fm.syncLosses, lsm.syncLosses, lsmV2.syncLosses);
        System.out.printf("  │ LDU Frames               │ %8d │ %8d │ %13d │%n",
                c4fm.lduCount, lsm.lduCount, lsmV2.lduCount);
        System.out.printf("  │ Audio (LDU x 180ms)      │ %7.1fs │ %7.1fs │ %12.1fs │%n",
                c4fm.lduCount * 0.18, lsm.lduCount * 0.18, lsmV2.lduCount * 0.18);
        System.out.printf("  │ HDU Count                │ %8d │ %8d │ %13d │%n",
                c4fm.hduTimestamps.size(),
                lsm.hduTimestamps.size(), lsmV2.hduTimestamps.size());
        System.out.printf("  │ TDU Count                │ %8d │ %8d │ %13d │%n",
                c4fm.tduTimestamps.size(),
                lsm.tduTimestamps.size(), lsmV2.tduTimestamps.size());
        System.out.printf("  │ Bit Errors               │ %8d │ %8d │ %13d │%n",
                c4fm.bitErrors, lsm.bitErrors, lsmV2.bitErrors);
        System.out.printf("  │ Max Bit Errors/Msg       │ %8d │ %8d │ %13d │%n",
                c4fm.maxBitErrorsPerMessage,
                lsm.maxBitErrorsPerMessage, lsmV2.maxBitErrorsPerMessage);

        double c4fmRate = c4fm.validMessageRate();
        double lsmRate = lsm.validMessageRate();
        double lsmV2Rate = lsmV2.validMessageRate();
        System.out.printf("  │ Valid Rate               │ %7.1f%% │ %7.1f%% │ %12.1f%% │%n",
                c4fmRate, lsmRate, lsmV2Rate);
        System.out.printf("  │ Error-Free Msg Rate      │ %7.1f%% │ %7.1f%% │ %12.1f%% │%n",
                c4fm.errorFreeMessageRate(),
                lsm.errorFreeMessageRate(), lsmV2.errorFreeMessageRate());
        System.out.printf("  │ Est. BER                 │ %7.3f%% │ %7.3f%% │ %12.3f%% │%n",
                c4fm.estimatedBER(), lsm.estimatedBER(), lsmV2.estimatedBER());

        // Framer diagnostics
        if(lsmV2.syncDetectionCount > 0)
        {
            System.out.println("  ├─────────────────────────┼──────────┼──────────┼───────────────┤");
            System.out.println("  │ Framer Diagnostics      │   C4FM   │   LSM    │    LSM v2     │");
            System.out.println("  ├─────────────────────────┼──────────┼──────────┼───────────────┤");
            System.out.printf("  │ Sync Detections          │ %8d │ %8d │ %13d │%n",
                    c4fm.syncDetectionCount,
                    lsm.syncDetectionCount, lsmV2.syncDetectionCount);
            System.out.printf("  │ NID Success              │ %8d │ %8d │ %13d │%n",
                    c4fm.nidDecodeSuccessCount,
                    lsm.nidDecodeSuccessCount, lsmV2.nidDecodeSuccessCount);
            System.out.printf("  │ NID Fail                 │ %8d │ %8d │ %13d │%n",
                    c4fm.nidDecodeFailCount,
                    lsm.nidDecodeFailCount, lsmV2.nidDecodeFailCount);
            System.out.printf("  │ NID Success Rate         │ %7.1f%% │ %7.1f%% │ %12.1f%% │%n",
                    c4fm.nidSuccessRate(),
                    lsm.nidSuccessRate(), lsmV2.nidSuccessRate());
            System.out.printf("  │ Fallback Sync            │ %8d │ %8d │ %13d │%n",
                    c4fm.fallbackSyncCount,
                    lsm.fallbackSyncCount, lsmV2.fallbackSyncCount);
            System.out.printf("  │ Recovery Sync            │ %8d │ %8d │ %13d │%n",
                    c4fm.recoverySyncCount,
                    lsm.recoverySyncCount, lsmV2.recoverySyncCount);
            System.out.printf("  │ Fade Recovery Sync       │ %8d │ %8d │ %13d │%n",
                    c4fm.fadeRecoverySyncCount,
                    lsm.fadeRecoverySyncCount, lsmV2.fadeRecoverySyncCount);
            System.out.printf("  │ Sync Blocked (Guard)     │ %8d │ %8d │ %13d │%n",
                    c4fm.syncBlockedCount,
                    lsm.syncBlockedCount, lsmV2.syncBlockedCount);
        }

        System.out.println("  └─────────────────────────┴──────────┴──────────┴───────────────┘");

        // Highlight best decoder
        int bestLDU = Math.max(Math.max(c4fm.lduCount, lsm.lduCount), lsmV2.lduCount);
        String bestDecoder = c4fm.lduCount == bestLDU ? "C4FM" :
                             lsm.lduCount == bestLDU ? "LSM" : "LSM v2";
        int bestValid = Math.max(Math.max(c4fm.validMessages, lsm.validMessages), lsmV2.validMessages);
        String bestValid_ = c4fm.validMessages == bestValid ? "C4FM" :
                             lsm.validMessages == bestValid ? "LSM" : "LSM v2";
        System.out.printf("  >>> Best LDU count: %s (%d) | Best valid msgs: %s (%d)%n",
                bestDecoder, bestLDU, bestValid_, bestValid);
    }

    private static String padRight(String s, int n)
    {
        return String.format("%-" + n + "s", s);
    }

    /**
     * Finds LDU timestamps decoded by decoder A but not by decoder B.
     */
    private static int findUniqueCount(DecoderStats a, DecoderStats b)
    {
        if(a.lduTimestamps.isEmpty() || b.lduTimestamps.isEmpty())
        {
            return a.lduCount;
        }

        long aBase = a.allMessageTimestamps.isEmpty() ? 0 : a.allMessageTimestamps.getFirst();
        long bBase = b.allMessageTimestamps.isEmpty() ? 0 : b.allMessageTimestamps.getFirst();

        List<Long> aRelative = a.lduTimestamps.stream().map(ts -> ts - aBase).toList();
        List<Long> bRelative = b.lduTimestamps.stream().map(ts -> ts - bBase).toList();

        int unique = 0;
        final long TOLERANCE_MS = 200;

        for(Long aTs : aRelative)
        {
            boolean foundInB = false;
            for(Long bTs : bRelative)
            {
                if(Math.abs(aTs - bTs) <= TOLERANCE_MS)
                {
                    foundInB = true;
                    break;
                }
            }
            if(!foundInB)
            {
                unique++;
            }
        }
        return unique;
    }

    private static void determineWinner(DecoderStats c4fm, DecoderStats lsm, DecoderStats lsmV2)
    {
        // Score each decoder on multiple metrics
        int c4fmScore = 0, lsmScore = 0, lsmV2Score = 0;

        // LDU count (most important - audio output)
        int maxLDU = Math.max(Math.max(c4fm.lduCount, lsm.lduCount), lsmV2.lduCount);
        if(c4fm.lduCount == maxLDU) c4fmScore += 3;
        if(lsm.lduCount == maxLDU) lsmScore += 3;
        if(lsmV2.lduCount == maxLDU) lsmV2Score += 3;

        // Valid messages
        int maxValid = Math.max(Math.max(c4fm.validMessages, lsm.validMessages), lsmV2.validMessages);
        if(c4fm.validMessages == maxValid) c4fmScore += 2;
        if(lsm.validMessages == maxValid) lsmScore += 2;
        if(lsmV2.validMessages == maxValid) lsmV2Score += 2;

        // Lowest sync losses (better = fewer)
        int minSync = Math.min(Math.min(c4fm.syncLosses, lsm.syncLosses), lsmV2.syncLosses);
        if(c4fm.syncLosses == minSync) c4fmScore += 1;
        if(lsm.syncLosses == minSync) lsmScore += 1;
        if(lsmV2.syncLosses == minSync) lsmV2Score += 1;

        // Lowest bit errors (better = fewer, normalized by LDU)
        double c4fmBER = c4fm.estimatedBER();
        double lsmBER = lsm.estimatedBER();
        double lsmV2BER = lsmV2.estimatedBER();
        double minBER = Math.min(Math.min(c4fmBER, lsmBER), lsmV2BER);
        if(c4fmBER <= minBER + 0.001) c4fmScore += 1;
        if(lsmBER <= minBER + 0.001) lsmScore += 1;
        if(lsmV2BER <= minBER + 0.001) lsmV2Score += 1;

        System.out.printf("C4FM score: %d | LSM score: %d | LSM v2 score: %d%n",
                c4fmScore, lsmScore, lsmV2Score);

        int maxScore = Math.max(Math.max(c4fmScore, lsmScore), lsmV2Score);
        String winner = c4fmScore == maxScore ? "C4FM" :
                         lsmScore == maxScore ? "LSM" : "LSM v2";
        System.out.println("RECOMMENDED DECODER: " + winner);

        // Print percentage differences from best LDU count
        if(maxLDU > 0)
        {
            System.out.println();
            System.out.println("LDU recovery vs best:");
            System.out.printf("  C4FM:   %d LDUs (%+.1f%% vs best)%n", c4fm.lduCount,
                    (c4fm.lduCount - maxLDU) * 100.0 / maxLDU);
            System.out.printf("  LSM:    %d LDUs (%+.1f%% vs best)%n", lsm.lduCount,
                    (lsm.lduCount - maxLDU) * 100.0 / maxLDU);
            System.out.printf("  LSMv2:  %d LDUs (%+.1f%% vs best)%n", lsmV2.lduCount,
                    (lsmV2.lduCount - maxLDU) * 100.0 / maxLDU);
        }
    }

    // ───────────────────────────── Decoder Runners ─────────────────────────────

    private static DecoderStats runC4FMDecoder(File file)
    {
        DecoderStats stats = new DecoderStats();
        Listener<IMessage> listener = createMessageListener(stats);

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            P25P1DecoderC4FM decoder = new P25P1DecoderC4FM();
            decoder.setMessageListener(listener);
            decoder.start();

            source.setListener(iNativeBuffer -> {
                var it = iNativeBuffer.iterator();
                while(it.hasNext())
                {
                    decoder.receive(it.next());
                }
            });
            source.start();
            decoder.setSampleRate(source.getSampleRate());

            processFile(source);

            // Extract framer diagnostics via reflection (C4FM doesn't expose getMessageFramer)
            extractFramerDiagnostics(decoder, "mMessageFramer", stats);

            decoder.stop();
        }
        catch(IOException e)
        {
            System.out.println("  ERROR: " + e.getMessage());
        }

        return stats;
    }

    private static DecoderStats runLSMDecoder(File file)
    {
        DecoderStats stats = new DecoderStats();
        Listener<IMessage> listener = createMessageListener(stats);

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            P25P1DecoderLSM decoder = new P25P1DecoderLSM();
            decoder.setMessageListener(listener);
            decoder.start();

            source.setListener(iNativeBuffer -> {
                var it = iNativeBuffer.iterator();
                while(it.hasNext())
                {
                    decoder.receive(it.next());
                }
            });
            source.start();
            decoder.setSampleRate(source.getSampleRate());

            processFile(source);

            // Extract framer diagnostics via reflection
            extractFramerDiagnostics(decoder, "mMessageFramer", stats);

            decoder.stop();
        }
        catch(IOException e)
        {
            System.out.println("  ERROR: " + e.getMessage());
        }

        return stats;
    }

    private static DecoderStats runLSMv2Decoder(File file, String[] diagnosticsOut)
    {
        DecoderStats stats = new DecoderStats();
        Listener<IMessage> listener = createMessageListener(stats);

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
            decoder.setMessageListener(listener);

            if(sConfiguredNAC > 0)
            {
                decoder.setConfiguredNAC(sConfiguredNAC);
            }

            decoder.start();

            source.setListener(iNativeBuffer -> {
                var it = iNativeBuffer.iterator();
                while(it.hasNext())
                {
                    decoder.receive(it.next());
                }
            });
            source.start();
            decoder.setSampleRate(source.getSampleRate());

            processFile(source);

            diagnosticsOut[0] = decoder.getDiagnostics();

            // Extract framer diagnostics via public accessor
            P25P1MessageFramer framer = decoder.getMessageFramer();
            stats.syncDetectionCount = framer.getSyncDetectionCount();
            stats.nidDecodeSuccessCount = framer.getNIDDecodeSuccessCount();
            stats.nidDecodeFailCount = framer.getNIDDecodeFailCount();
            stats.fallbackSyncCount = framer.getFallbackSyncCount();
            stats.recoverySyncCount = framer.getRecoverySyncCount();
            stats.fadeRecoverySyncCount = framer.getFadeRecoverySyncCount();
            stats.syncBlockedCount = framer.getSyncBlockedCount();

            decoder.stop();
        }
        catch(IOException e)
        {
            System.out.println("  ERROR: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Extracts framer diagnostics from a decoder via reflection, for decoders that don't
     * expose a public getMessageFramer() method.
     */
    private static void extractFramerDiagnostics(Object decoder, String fieldName, DecoderStats stats)
    {
        try
        {
            Field framerField = decoder.getClass().getDeclaredField(fieldName);
            framerField.setAccessible(true);
            P25P1MessageFramer framer = (P25P1MessageFramer) framerField.get(decoder);

            stats.syncDetectionCount = framer.getSyncDetectionCount();
            stats.nidDecodeSuccessCount = framer.getNIDDecodeSuccessCount();
            stats.nidDecodeFailCount = framer.getNIDDecodeFailCount();
            stats.fallbackSyncCount = framer.getFallbackSyncCount();
            stats.recoverySyncCount = framer.getRecoverySyncCount();
            stats.fadeRecoverySyncCount = framer.getFadeRecoverySyncCount();
            stats.syncBlockedCount = framer.getSyncBlockedCount();
        }
        catch(NoSuchFieldException | IllegalAccessException e)
        {
            System.out.println("  WARNING: Could not extract framer diagnostics: " + e.getMessage());
        }
    }

    private static Listener<IMessage> createMessageListener(DecoderStats stats)
    {
        return iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                stats.totalMessages++;
                stats.allMessageTimestamps.add(message.getTimestamp());

                if(message.isValid())
                {
                    stats.validMessages++;
                    int correctedBits = message.getMessage() != null ?
                            Math.max(message.getMessage().getCorrectedBitCount(), 0) : 0;
                    if(correctedBits > 0)
                    {
                        stats.messagesWithBitErrors++;
                        stats.maxBitErrorsPerMessage = Math.max(stats.maxBitErrorsPerMessage, correctedBits);
                        stats.messageBitErrors.add(new long[]{message.getTimestamp(), correctedBits});
                    }
                    stats.bitErrors += correctedBits;

                    P25P1DataUnitID duid = message.getDUID();
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2)
                    {
                        stats.lduCount++;
                        stats.lduTimestamps.add(message.getTimestamp());
                    }
                    else if(duid == P25P1DataUnitID.HEADER_DATA_UNIT)
                    {
                        stats.hduTimestamps.add(message.getTimestamp());
                    }
                    else if(duid == P25P1DataUnitID.TERMINATOR_DATA_UNIT ||
                            duid == P25P1DataUnitID.TERMINATOR_DATA_UNIT_LINK_CONTROL)
                    {
                        stats.tduTimestamps.add(message.getTimestamp());
                    }
                }
                else
                {
                    stats.invalidMessages++;
                    stats.invalidMessageTimestamps.add(message.getTimestamp());
                }
            }
            else if(iMessage instanceof SyncLossMessage)
            {
                stats.syncLosses++;
                stats.syncLossTimestamps.add(iMessage.getTimestamp());
            }
        };
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

    // ───────────────────────────── Stats Class ─────────────────────────────

    static class DecoderStats
    {
        int validMessages = 0;
        int totalMessages = 0;
        int syncLosses = 0;
        int bitErrors = 0;
        int lduCount = 0;

        int invalidMessages = 0;
        int messagesWithBitErrors = 0;
        int maxBitErrorsPerMessage = 0;

        // Framer diagnostics
        int syncDetectionCount = 0;
        int nidDecodeSuccessCount = 0;
        int nidDecodeFailCount = 0;
        int fallbackSyncCount = 0;
        int recoverySyncCount = 0;
        int fadeRecoverySyncCount = 0;
        int syncBlockedCount = 0;

        // Timestamp tracking
        List<Long> lduTimestamps = new ArrayList<>();
        List<Long> syncLossTimestamps = new ArrayList<>();
        List<Long> allMessageTimestamps = new ArrayList<>();
        List<Long> hduTimestamps = new ArrayList<>();
        List<Long> tduTimestamps = new ArrayList<>();
        List<long[]> messageBitErrors = new ArrayList<>();
        List<Long> invalidMessageTimestamps = new ArrayList<>();

        /**
         * Accumulates stats from another DecoderStats instance (for multi-file aggregation).
         */
        void accumulate(DecoderStats other)
        {
            validMessages += other.validMessages;
            totalMessages += other.totalMessages;
            syncLosses += other.syncLosses;
            bitErrors += other.bitErrors;
            lduCount += other.lduCount;
            invalidMessages += other.invalidMessages;
            messagesWithBitErrors += other.messagesWithBitErrors;
            maxBitErrorsPerMessage = Math.max(maxBitErrorsPerMessage, other.maxBitErrorsPerMessage);

            syncDetectionCount += other.syncDetectionCount;
            nidDecodeSuccessCount += other.nidDecodeSuccessCount;
            nidDecodeFailCount += other.nidDecodeFailCount;
            fallbackSyncCount += other.fallbackSyncCount;
            recoverySyncCount += other.recoverySyncCount;
            fadeRecoverySyncCount += other.fadeRecoverySyncCount;
            syncBlockedCount += other.syncBlockedCount;

            lduTimestamps.addAll(other.lduTimestamps);
            syncLossTimestamps.addAll(other.syncLossTimestamps);
            allMessageTimestamps.addAll(other.allMessageTimestamps);
            hduTimestamps.addAll(other.hduTimestamps);
            tduTimestamps.addAll(other.tduTimestamps);
            messageBitErrors.addAll(other.messageBitErrors);
            invalidMessageTimestamps.addAll(other.invalidMessageTimestamps);
        }

        List<long[]> findLduGaps(long maxGapMs)
        {
            List<long[]> gaps = new ArrayList<>();
            if(lduTimestamps.size() < 2) return gaps;

            for(int i = 1; i < lduTimestamps.size(); i++)
            {
                long gap = lduTimestamps.get(i) - lduTimestamps.get(i - 1);
                if(gap > maxGapMs)
                {
                    gaps.add(new long[]{lduTimestamps.get(i - 1), gap});
                }
            }
            return gaps;
        }

        double nidSuccessRate()
        {
            return syncDetectionCount > 0 ?
                    (double) nidDecodeSuccessCount / syncDetectionCount * 100.0 : 0;
        }

        double validMessageRate()
        {
            return totalMessages > 0 ?
                    (double) validMessages / totalMessages * 100.0 : 0;
        }

        double estimatedBER()
        {
            int estimatedBits = lduCount * 1568;
            return estimatedBits > 0 ?
                    (double) bitErrors / estimatedBits * 100.0 : 0;
        }

        double errorFreeMessageRate()
        {
            return validMessages > 0 ?
                    (double) (validMessages - messagesWithBitErrors) / validMessages * 100.0 : 0;
        }
    }
}
