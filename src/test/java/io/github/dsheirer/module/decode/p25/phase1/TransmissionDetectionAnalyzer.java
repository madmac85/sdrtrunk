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
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Multi-file analyzer for transmission detection metrics.
 * Processes multiple baseband files and generates aggregate statistics
 * comparing expected transmissions (from signal detection) against
 * actual decode performance for both LSM and LSM v2 decoders.
 *
 * Usage: java TransmissionDetectionAnalyzer <file1.wav> [file2.wav] ...
 *    or: java TransmissionDetectionAnalyzer --dir <directory>
 */
public class TransmissionDetectionAnalyzer
{
    private static int sConfiguredNAC = 0;

    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: TransmissionDetectionAnalyzer <file1.wav> [file2.wav] ...");
            System.out.println("   or: TransmissionDetectionAnalyzer --dir <directory> [nac]");
            System.out.println();
            System.out.println("Analyzes transmission detection vs decode performance for LSM and v2 decoders.");
            System.out.println("Generates per-file and aggregate metrics showing expected vs decoded transmissions.");
            return;
        }

        List<File> files = new ArrayList<>();

        // Parse arguments
        for(int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            if(arg.equals("--dir") && i + 1 < args.length)
            {
                File dir = new File(args[++i]);
                if(dir.isDirectory())
                {
                    File[] wavFiles = dir.listFiles((d, name) ->
                        name.endsWith("_baseband.wav") && !name.contains("audio"));
                    if(wavFiles != null)
                    {
                        for(File f : wavFiles)
                        {
                            files.add(f);
                        }
                    }
                }
                else
                {
                    System.out.println("ERROR: Not a directory: " + dir);
                    return;
                }
            }
            else if(arg.matches("\\d+"))
            {
                sConfiguredNAC = Integer.parseInt(arg);
            }
            else if(!arg.startsWith("--"))
            {
                File f = new File(arg);
                if(f.exists())
                {
                    files.add(f);
                }
                else
                {
                    System.out.println("WARNING: File not found: " + arg);
                }
            }
        }

        if(files.isEmpty())
        {
            System.out.println("ERROR: No valid files found");
            return;
        }

        // Sort files by name
        files.sort((a, b) -> a.getName().compareTo(b.getName()));

        System.out.println("=== TRANSMISSION DETECTION ANALYSIS ===");
        System.out.println("Files: " + files.size());
        if(sConfiguredNAC > 0)
        {
            System.out.println("NAC: " + sConfiguredNAC);
        }
        System.out.println();

        // Process all files and collect metrics
        DetectionMetrics aggregateMetrics = new DetectionMetrics();
        List<FileResult> fileResults = new ArrayList<>();

        for(File file : files)
        {
            try
            {
                FileResult result = analyzeFile(file);
                fileResults.add(result);
                aggregateMetrics.merge(result.metrics);
            }
            catch(Exception e)
            {
                System.out.println("ERROR processing " + file.getName() + ": " + e.getMessage());
            }
        }

        // Print per-file summary
        printPerFileSummary(fileResults);

        // Print aggregate metrics
        System.out.println();
        System.out.println("=== AGGREGATE RESULTS (" + files.size() + " files) ===");
        System.out.println(aggregateMetrics.toReport());

        // Print status breakdown
        printStatusBreakdown(fileResults);
    }

    /**
     * Analyzes a single file and returns metrics.
     */
    private static FileResult analyzeFile(File file) throws IOException, UnsupportedAudioFileException
    {
        System.out.println("Processing: " + file.getName());

        // Step 1: Detect transmissions
        TransmissionMapper mapper = new TransmissionMapper();
        List<Transmission> transmissions = mapper.mapTransmissions(file);

        if(transmissions.isEmpty())
        {
            System.out.println("  No transmissions detected");
            return new FileResult(file.getName(), new DetectionMetrics(), 0, 0, 0);
        }

        // Step 2: Run both decoders
        LSMv2ComparisonTest.DecoderStats lsmStats = runDecoder(file, false);
        LSMv2ComparisonTest.DecoderStats v2Stats = runDecoder(file, true);

        // Step 3: Score transmissions
        TransmissionScorer scorer = new TransmissionScorer();
        List<TransmissionDecodeResult> results = new ArrayList<>();

        for(Transmission tx : transmissions)
        {
            results.add(scorer.scoreForDetectionMetrics(tx, lsmStats, v2Stats, 0));
        }

        // Step 4: Calculate metrics
        DetectionMetrics metrics = new DetectionMetrics(results);

        System.out.println(String.format("  TX: %d expected, LSM decoded %d, v2 decoded %d | LDU: %d expected, LSM %d, v2 %d",
            metrics.getTotalExpectedTransmissions(),
            metrics.getLsmDecodedTransmissions(),
            metrics.getV2DecodedTransmissions(),
            metrics.getTotalExpectedLDUs(),
            metrics.getLsmDecodedLDUs(),
            metrics.getV2DecodedLDUs()));

        return new FileResult(
            file.getName(),
            metrics,
            transmissions.size(),
            lsmStats.lduCount,
            v2Stats.lduCount
        );
    }

    /**
     * Runs a decoder on the file and returns stats.
     */
    private static LSMv2ComparisonTest.DecoderStats runDecoder(File file, boolean useV2)
        throws IOException, UnsupportedAudioFileException
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

        return stats;
    }

    private static void printPerFileSummary(List<FileResult> results)
    {
        System.out.println();
        System.out.println("=== PER-FILE SUMMARY ===");
        System.out.println(String.format("%-50s %6s %8s %8s %8s %8s %8s",
            "File", "ExpTX", "ExpLDU", "LSM_TX", "v2_TX", "LSM_LDU", "v2_LDU"));
        System.out.println("-".repeat(110));

        for(FileResult r : results)
        {
            String shortName = r.fileName.length() > 48 ?
                r.fileName.substring(0, 45) + "..." : r.fileName;
            System.out.println(String.format("%-50s %6d %8d %8d %8d %8d %8d",
                shortName,
                r.metrics.getTotalExpectedTransmissions(),
                r.metrics.getTotalExpectedLDUs(),
                r.metrics.getLsmDecodedTransmissions(),
                r.metrics.getV2DecodedTransmissions(),
                r.metrics.getLsmDecodedLDUs(),
                r.metrics.getV2DecodedLDUs()));
        }
    }

    private static void printStatusBreakdown(List<FileResult> results)
    {
        int totalBoth = 0, totalLsmOnly = 0, totalV2Only = 0, totalMissed = 0;

        for(FileResult r : results)
        {
            totalBoth += r.metrics.getBothDecodedCount();
            totalLsmOnly += r.metrics.getLsmOnlyCount();
            totalV2Only += r.metrics.getV2OnlyCount();
            totalMissed += r.metrics.getMissedCount();
        }

        int total = totalBoth + totalLsmOnly + totalV2Only + totalMissed;

        System.out.println();
        System.out.println("=== TRANSMISSION STATUS BREAKDOWN ===");
        System.out.println(String.format("Both decoders:  %5d (%5.1f%%)", totalBoth,
            total > 0 ? (double)totalBoth / total * 100 : 0));
        System.out.println(String.format("v2 only:        %5d (%5.1f%%)", totalV2Only,
            total > 0 ? (double)totalV2Only / total * 100 : 0));
        System.out.println(String.format("LSM only:       %5d (%5.1f%%)", totalLsmOnly,
            total > 0 ? (double)totalLsmOnly / total * 100 : 0));
        System.out.println(String.format("Neither:        %5d (%5.1f%%)", totalMissed,
            total > 0 ? (double)totalMissed / total * 100 : 0));
        System.out.println();
        System.out.println(String.format("v2-exclusive transmissions: %d (%.1f%% of expected)",
            totalV2Only, total > 0 ? (double)totalV2Only / total * 100 : 0));
    }

    /**
     * Result container for a single file analysis.
     */
    private record FileResult(
        String fileName,
        DetectionMetrics metrics,
        int transmissionCount,
        int lsmLduCount,
        int v2LduCount
    ) {}
}
