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

import io.github.dsheirer.buffer.INativeBuffer;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Transmission scoring test that:
 * 1. Detects transmission boundaries using signal energy
 * 2. Runs both LSM and LSM v2 decoders
 * 3. Scores each transmission's decode completeness
 * 4. Generates a comparative report
 * 5. Optionally extracts worst/regression transmissions
 *
 * Usage: java TransmissionScoringTest <path-to-baseband.wav> [nac] [--extract-worst N] [--extract-regressions] [--output-dir dir]
 */
public class TransmissionScoringTest
{
    private static int sConfiguredNAC = 0;
    private static int sExtractWorst = 0;
    private static boolean sExtractRegressions = false;
    private static File sOutputDir = null;

    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: TransmissionScoringTest <path-to-baseband.wav> [nac] [options]");
            System.out.println("  Analyzes transmission decode quality for LSM vs LSM v2 decoders.");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println("  baseband.wav         - Input baseband recording");
            System.out.println("  nac                  - Optional: Known NAC value (0-4095)");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --extract-worst N    - Extract N worst-performing transmissions");
            System.out.println("  --extract-regressions - Extract all regression transmissions");
            System.out.println("  --output-dir dir     - Output directory for extracts (default: ./extracts)");
            return;
        }

        String filePath = args[0];
        File file = new File(filePath);

        if(!file.exists())
        {
            System.out.println("ERROR: File not found: " + filePath);
            return;
        }

        // Parse arguments
        for(int i = 1; i < args.length; i++)
        {
            String arg = args[i];
            if(arg.equals("--extract-worst") && i + 1 < args.length)
            {
                try
                {
                    sExtractWorst = Integer.parseInt(args[++i]);
                }
                catch(NumberFormatException e)
                {
                    System.out.println("WARNING: Invalid --extract-worst value");
                }
            }
            else if(arg.equals("--extract-regressions"))
            {
                sExtractRegressions = true;
            }
            else if(arg.equals("--output-dir") && i + 1 < args.length)
            {
                sOutputDir = new File(args[++i]);
            }
            else if(!arg.startsWith("--"))
            {
                // Try parsing as NAC
                try
                {
                    int nac = Integer.parseInt(arg);
                    if(nac >= 0 && nac <= 4095)
                    {
                        sConfiguredNAC = nac;
                    }
                    else
                    {
                        System.out.println("WARNING: NAC must be 0-4095, ignoring: " + nac);
                    }
                }
                catch(NumberFormatException e)
                {
                    System.out.println("WARNING: Unknown argument: " + arg);
                }
            }
        }

        // Default output directory
        if(sOutputDir == null && (sExtractWorst > 0 || sExtractRegressions))
        {
            sOutputDir = new File("extracts");
        }

        try
        {
            runAnalysis(file);
        }
        catch(Exception e)
        {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runAnalysis(File file) throws Exception
    {
        System.out.println("=== Transmission Scoring Analysis ===");
        System.out.println("File: " + file.getName());
        if(sConfiguredNAC > 0)
        {
            System.out.println("NAC: " + sConfiguredNAC);
        }
        System.out.println();

        // Step 1: Map transmissions using energy detection
        System.out.println("--- Mapping Transmissions ---");
        TransmissionMapper mapper = new TransmissionMapper();
        List<Transmission> transmissions = mapper.mapTransmissions(file);
        System.out.println("Detected " + transmissions.size() + " transmissions");
        System.out.println("Recording duration: " + mapper.getTotalDurationMs() + " ms");
        System.out.println();

        if(transmissions.isEmpty())
        {
            System.out.println("No transmissions detected. Check signal levels.");
            return;
        }

        // Step 2: Run both decoders
        System.out.println("--- Running LSM Decoder ---");
        LSMv2ComparisonTest.DecoderStats lsmStats = runDecoder(file, false);

        System.out.println("--- Running LSM v2 Decoder ---");
        LSMv2ComparisonTest.DecoderStats v2Stats = runDecoder(file, true);
        System.out.println();

        // Base timestamp is 0 since TestComplexWaveSource provides file-position-based timestamps
        // starting from 0. We don't need to normalize.
        long baseTimestamp = 0;


        // Step 3: Score each transmission
        System.out.println("--- Scoring Transmissions ---");
        TransmissionScorer scorer = new TransmissionScorer();
        List<TransmissionScore> scores = new ArrayList<>();

        for(Transmission tx : transmissions)
        {
            scores.add(scorer.score(tx, lsmStats, v2Stats, baseTimestamp));
        }

        // Step 4: Generate report
        printDetailedReport(scores);
        printSummary(scores, lsmStats, v2Stats);
        printErrorMetrics(scores, lsmStats, v2Stats);
        printRegressions(scores);
        printWorstTransmissions(scores);

        // Step 5: Extract transmissions if requested
        if(sExtractWorst > 0 || sExtractRegressions)
        {
            try
            {
                TransmissionExtractor extractor = new TransmissionExtractor();

                if(sExtractWorst > 0)
                {
                    System.out.println();
                    System.out.println("=== Extracting Worst Transmissions ===");
                    extractor.extractWorstTransmissions(file, sOutputDir, scores, sExtractWorst, 200);
                }

                if(sExtractRegressions)
                {
                    System.out.println();
                    System.out.println("=== Extracting Regressions ===");
                    extractor.extractRegressions(file, sOutputDir, scores, 200);
                }

                System.out.println();
                System.out.println("Extracts saved to: " + sOutputDir.getAbsolutePath());
            }
            catch(Exception e)
            {
                System.out.println("ERROR extracting transmissions: " + e.getMessage());
            }
        }
    }

    private static void printDetailedReport(List<TransmissionScore> scores)
    {
        System.out.println("=== Per-Transmission Scores ===");
        System.out.println(String.format(
            "%-4s %8s %8s %8s %5s %6s %6s %6s %6s %3s %3s %6s %6s  %-20s",
            "ID", "Start", "End", "Duration", "Exp", "LSM", "v2", "LSM%", "v2%", "HDU", "TDU", "Lerr", "v2err", "Flags"
        ));
        System.out.println("-".repeat(120));

        for(TransmissionScore score : scores)
        {
            Transmission tx = score.transmission();
            System.out.println(String.format(
                "%-4d %8d %8d %8d %5d %6d %6d %5.1f%% %5.1f%% %3s %3s %6d %6d  %-20s",
                tx.index(),
                tx.startMs(),
                tx.endMs(),
                tx.durationMs(),
                tx.expectedLDUs(),
                score.lsmLduCount(),
                score.v2LduCount(),
                score.lsmScore(),
                score.v2Score(),
                score.v2HasHDU() ? "Y" : "N",
                score.v2HasTDU() ? "Y" : "N",
                score.lsmBitErrors(),
                score.v2BitErrors(),
                score.flagsString()
            ));
        }
        System.out.println();
    }

    private static void printSummary(List<TransmissionScore> scores,
                                     LSMv2ComparisonTest.DecoderStats lsmStats,
                                     LSMv2ComparisonTest.DecoderStats v2Stats)
    {
        System.out.println("=== Summary ===");

        int totalTx = scores.size();
        int completeFraming = (int)scores.stream().filter(TransmissionScore::hasCompleteFraming).count();
        int hasHDU = (int)scores.stream().filter(s -> s.v2HasHDU()).count();
        int hasTDU = (int)scores.stream().filter(s -> s.v2HasTDU()).count();

        double avgLsmScore = scores.stream().mapToDouble(TransmissionScore::lsmScore).average().orElse(0);
        double avgV2Score = scores.stream().mapToDouble(TransmissionScore::v2Score).average().orElse(0);

        int totalExpectedLDUs = scores.stream().mapToInt(s -> s.transmission().expectedLDUs()).sum();

        System.out.println("Total Transmissions: " + totalTx);
        System.out.println(String.format("With HDU (v2):       %d (%.1f%%)", hasHDU, (double)hasHDU / totalTx * 100));
        System.out.println(String.format("With TDU (v2):       %d (%.1f%%)", hasTDU, (double)hasTDU / totalTx * 100));
        System.out.println(String.format("Complete (HDU+TDU):  %d (%.1f%%)", completeFraming, (double)completeFraming / totalTx * 100));
        System.out.println();
        System.out.println("Total Expected LDUs: " + totalExpectedLDUs);
        System.out.println("Total LSM LDUs:      " + lsmStats.lduCount);
        System.out.println("Total v2 LDUs:       " + v2Stats.lduCount);
        System.out.println();
        System.out.println(String.format("Avg LSM Score:       %.1f%%", avgLsmScore));
        System.out.println(String.format("Avg v2 Score:        %.1f%%", avgV2Score));
        System.out.println(String.format("v2 Improvement:      %+.1f%%", avgV2Score - avgLsmScore));
        System.out.println();
    }

    private static void printErrorMetrics(List<TransmissionScore> scores,
                                          LSMv2ComparisonTest.DecoderStats lsmStats,
                                          LSMv2ComparisonTest.DecoderStats v2Stats)
    {
        System.out.println("=== ERROR METRICS ===");
        System.out.println(String.format("%-30s %10s %10s %10s", "", "LSM", "v2", "Delta"));
        System.out.println("-".repeat(62));

        // Total/Valid/Invalid Messages
        System.out.println(String.format("%-30s %10d %10d %+10d", "Total Messages",
            lsmStats.totalMessages, v2Stats.totalMessages,
            v2Stats.totalMessages - lsmStats.totalMessages));
        System.out.println(String.format("%-30s %10d %10d %+10d", "Valid Messages",
            lsmStats.validMessages, v2Stats.validMessages,
            v2Stats.validMessages - lsmStats.validMessages));
        System.out.println(String.format("%-30s %10d %10d %+10d", "Invalid Messages",
            lsmStats.invalidMessages, v2Stats.invalidMessages,
            v2Stats.invalidMessages - lsmStats.invalidMessages));

        // Bit error stats
        System.out.println(String.format("%-30s %10d %10d %+10d", "Messages with Bit Errors",
            lsmStats.messagesWithBitErrors, v2Stats.messagesWithBitErrors,
            v2Stats.messagesWithBitErrors - lsmStats.messagesWithBitErrors));
        System.out.println(String.format("%-30s %10d %10d %+10d", "Total Bit Errors Corrected",
            lsmStats.bitErrors, v2Stats.bitErrors,
            v2Stats.bitErrors - lsmStats.bitErrors));
        System.out.println(String.format("%-30s %10d %10d %+10d", "Max Errors in Single Msg",
            lsmStats.maxBitErrorsPerMessage, v2Stats.maxBitErrorsPerMessage,
            v2Stats.maxBitErrorsPerMessage - lsmStats.maxBitErrorsPerMessage));

        // Rate metrics
        System.out.println(String.format("%-30s %9.2f%% %9.2f%% %+9.2f%%", "Est. Bit Error Rate (BER)",
            lsmStats.estimatedBER(), v2Stats.estimatedBER(),
            v2Stats.estimatedBER() - lsmStats.estimatedBER()));
        System.out.println(String.format("%-30s %9.1f%% %9.1f%% %+9.1f%%", "Error-Free Message Rate",
            lsmStats.errorFreeMessageRate(), v2Stats.errorFreeMessageRate(),
            v2Stats.errorFreeMessageRate() - lsmStats.errorFreeMessageRate()));
        System.out.println(String.format("%-30s %9.1f%% %9.1f%% %+9.1f%%", "Valid Message Rate",
            lsmStats.validMessageRate(), v2Stats.validMessageRate(),
            v2Stats.validMessageRate() - lsmStats.validMessageRate()));

        // v2-only sync/NID diagnostics
        if(v2Stats.syncDetectionCount > 0)
        {
            System.out.println();
            System.out.println("=== SYNC/NID DIAGNOSTICS (v2 only) ===");
            System.out.println(String.format("Sync Detections:          %d", v2Stats.syncDetectionCount));
            System.out.println(String.format("NID Decode Success:       %d (%.1f%%)",
                v2Stats.nidDecodeSuccessCount, v2Stats.nidSuccessRate()));
            System.out.println(String.format("NID Decode Fail:          %d (%.1f%%)",
                v2Stats.nidDecodeFailCount,
                v2Stats.syncDetectionCount > 0 ?
                    (double)v2Stats.nidDecodeFailCount / v2Stats.syncDetectionCount * 100.0 : 0));
            System.out.println(String.format("  Fallback Sync:          %d (%.1f%%)",
                v2Stats.fallbackSyncCount,
                v2Stats.syncDetectionCount > 0 ?
                    (double)v2Stats.fallbackSyncCount / v2Stats.syncDetectionCount * 100.0 : 0));
            System.out.println(String.format("  Boundary Recovery:      %d (%.1f%%)",
                v2Stats.recoverySyncCount,
                v2Stats.syncDetectionCount > 0 ?
                    (double)v2Stats.recoverySyncCount / v2Stats.syncDetectionCount * 100.0 : 0));
            System.out.println(String.format("  Fade Recovery:          %d (%.1f%%)",
                v2Stats.fadeRecoverySyncCount,
                v2Stats.syncDetectionCount > 0 ?
                    (double)v2Stats.fadeRecoverySyncCount / v2Stats.syncDetectionCount * 100.0 : 0));
        }
        System.out.println();
    }

    private static void printRegressions(List<TransmissionScore> scores)
    {
        List<TransmissionScore> regressions = scores.stream()
            .filter(TransmissionScore::isV2Regression)
            .sorted(Comparator.comparingInt(TransmissionScore::delta))
            .toList();

        System.out.println("=== Regressions (v2 < LSM) ===");

        if(regressions.isEmpty())
        {
            System.out.println("None - v2 matches or exceeds LSM on all transmissions");
        }
        else
        {
            for(TransmissionScore s : regressions)
            {
                System.out.println(String.format(
                    "TX#%d: v2=%.1f%% vs LSM=%.1f%% (%d LDUs worse) at %dms",
                    s.transmission().index(),
                    s.v2Score(),
                    s.lsmScore(),
                    -s.delta(),
                    s.transmission().startMs()
                ));
            }
        }
        System.out.println();
    }

    private static void printWorstTransmissions(List<TransmissionScore> scores)
    {
        List<TransmissionScore> worstByScore = scores.stream()
            .sorted(Comparator.comparingDouble(TransmissionScore::v2Score))
            .limit(10)
            .toList();

        System.out.println("=== Worst Transmissions (v2 Score) ===");

        int rank = 1;
        for(TransmissionScore s : worstByScore)
        {
            String notes = "";
            if(!s.v2HasHDU()) notes += "no HDU, ";
            if(!s.v2HasTDU()) notes += "no TDU, ";
            if(s.isV2Regression()) notes += "regression, ";
            if(!notes.isEmpty()) notes = "(" + notes.substring(0, notes.length() - 2) + ")";

            System.out.println(String.format(
                "%2d. TX#%d: %.1f%% (%d/%d LDUs) at %d-%dms %s",
                rank++,
                s.transmission().index(),
                s.v2Score(),
                s.v2LduCount(),
                s.transmission().expectedLDUs(),
                s.transmission().startMs(),
                s.transmission().endMs(),
                notes
            ));
        }
        System.out.println();
    }

    /**
     * Runs a decoder on the file and returns stats.
     * Uses TestComplexWaveSource to provide file-position-based timestamps.
     */
    private static LSMv2ComparisonTest.DecoderStats runDecoder(File file, boolean useV2)
    {
        LSMv2ComparisonTest.DecoderStats stats = new LSMv2ComparisonTest.DecoderStats();

        Listener<IMessage> messageListener = iMessage -> {
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
            else if(iMessage instanceof SyncLossMessage syncLoss)
            {
                stats.syncLosses++;
                stats.syncLossTimestamps.add(syncLoss.getTimestamp());
            }
        };

        try(TestComplexWaveSource source = new TestComplexWaveSource(file))
        {
            if(useV2)
            {
                P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
                decoder.setMessageListener(messageListener);
                if(sConfiguredNAC > 0)
                {
                    decoder.setConfiguredNAC(sConfiguredNAC);
                }
                decoder.start();
                decoder.setSampleRate(source.getSampleRate());

                source.setListener(buffer -> {
                    Iterator<ComplexSamples> it = buffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });

                while(source.next(2048)) { }

                // Capture framer diagnostics for error rate analysis
                P25P1MessageFramer framer = decoder.getMessageFramer();
                stats.syncDetectionCount = framer.getSyncDetectionCount();
                stats.nidDecodeSuccessCount = framer.getNIDDecodeSuccessCount();
                stats.nidDecodeFailCount = framer.getNIDDecodeFailCount();
                stats.fallbackSyncCount = framer.getFallbackSyncCount();
                stats.recoverySyncCount = framer.getRecoverySyncCount();
                stats.fadeRecoverySyncCount = framer.getFadeRecoverySyncCount();

                decoder.stop();
            }
            else
            {
                P25P1DecoderLSM decoder = new P25P1DecoderLSM();
                decoder.setMessageListener(messageListener);
                decoder.start();
                decoder.setSampleRate(source.getSampleRate());

                source.setListener(buffer -> {
                    Iterator<ComplexSamples> it = buffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });

                while(source.next(2048)) { }
                decoder.stop();
            }
        }
        catch(IOException | UnsupportedAudioFileException e)
        {
            System.out.println("ERROR: " + e.getMessage());
        }

        System.out.println("  LDUs: " + stats.lduCount + " | HDUs: " + stats.hduTimestamps.size() +
            " | TDUs: " + stats.tduTimestamps.size());

        return stats;
    }
}
