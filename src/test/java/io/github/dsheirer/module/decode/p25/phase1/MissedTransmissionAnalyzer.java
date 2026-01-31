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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Analyzes missed transmissions and low decode rate transmissions to identify
 * optimization opportunities. Categorizes failures, correlates signal quality
 * with decode success, and generates actionable recommendations.
 *
 * Usage: java MissedTransmissionAnalyzer <file1.wav> [file2.wav] ...
 *    or: java MissedTransmissionAnalyzer --dir <directory> [--extract <outputDir>]
 */
public class MissedTransmissionAnalyzer
{
    // Threshold constants
    private static final long TOO_SHORT_THRESHOLD_MS = 180;   // One LDU duration
    private static final double LOW_RATE_THRESHOLD = 20.0;    // Configurable default
    private static final int SIGNAL_QUALITY_BINS = 10;        // Number of quality tiers
    private static final double WEAK_SIGNAL_PERCENTILE = 0.10; // Bottom 10%

    // State
    private static int sConfiguredNAC = 0;
    private static boolean sExtractEnabled = false;
    private static File sExtractDir = null;
    private static List<File> sSourceFiles = new ArrayList<>();

    // Collected results
    private final List<TransmissionDecodeResult> allResults = new ArrayList<>();
    private final List<MissedTransmission> missedTransmissions = new ArrayList<>();
    private final List<LowRateTransmission> lowRateTransmissions = new ArrayList<>();

    // Energy threshold calculated from data
    private float weakSignalThreshold = 0;

    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            printUsage();
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
            else if(arg.equals("--extract") && i + 1 < args.length)
            {
                sExtractEnabled = true;
                sExtractDir = new File(args[++i]);
            }
            else if(arg.equals("--nac") && i + 1 < args.length)
            {
                sConfiguredNAC = Integer.parseInt(args[++i]);
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
        files.sort(Comparator.comparing(File::getName));
        sSourceFiles = files;

        System.out.println("=== MISSED TRANSMISSION ANALYSIS ===");
        System.out.println("Files: " + files.size());
        if(sConfiguredNAC > 0)
        {
            System.out.println("NAC: " + sConfiguredNAC);
        }
        System.out.println();

        // Create analyzer and process files
        MissedTransmissionAnalyzer analyzer = new MissedTransmissionAnalyzer();

        for(File file : files)
        {
            try
            {
                analyzer.processFile(file);
            }
            catch(Exception e)
            {
                System.out.println("ERROR processing " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Generate and print reports
        analyzer.calculateWeakSignalThreshold();
        analyzer.categorizeFailures();
        analyzer.identifyLowRateTransmissions();

        System.out.println();
        analyzer.printFailureCategoryReport();

        System.out.println();
        analyzer.printLowRateReport();

        System.out.println();
        analyzer.printSignalQualityCorrelation();

        System.out.println();
        analyzer.printOptimizationOpportunities();

        System.out.println();
        analyzer.printRecommendations();

        // Extract if requested
        if(sExtractEnabled && sExtractDir != null && !files.isEmpty())
        {
            System.out.println();
            analyzer.extractMissedTransmissions(files);
        }
    }

    private static void printUsage()
    {
        System.out.println("Usage: MissedTransmissionAnalyzer <file1.wav> [file2.wav] ...");
        System.out.println("   or: MissedTransmissionAnalyzer --dir <directory> [--extract <outputDir>]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --dir <directory>     Process all *_baseband.wav files in directory");
        System.out.println("  --extract <outputDir> Extract missed transmissions to separate files");
        System.out.println("  --nac <value>         Set NAC filter value");
        System.out.println();
        System.out.println("Analyzes missed transmissions to identify optimization opportunities.");
        System.out.println("Categorizes failures, correlates signal quality, and provides recommendations.");
    }

    /**
     * Processes a single file and collects transmission decode results.
     */
    private void processFile(File file) throws IOException, UnsupportedAudioFileException
    {
        System.out.println("Processing: " + file.getName());

        // Step 1: Detect transmissions
        TransmissionMapper mapper = new TransmissionMapper();
        List<Transmission> transmissions = mapper.mapTransmissions(file);

        if(transmissions.isEmpty())
        {
            System.out.println("  No transmissions detected");
            return;
        }

        // Step 2: Run both decoders
        LSMv2ComparisonTest.DecoderStats lsmStats = runDecoder(file, false);
        LSMv2ComparisonTest.DecoderStats v2Stats = runDecoder(file, true);

        // Step 3: Score transmissions
        TransmissionScorer scorer = new TransmissionScorer();

        for(Transmission tx : transmissions)
        {
            TransmissionDecodeResult result = scorer.scoreForDetectionMetrics(tx, lsmStats, v2Stats, 0);
            allResults.add(result);
        }

        int missedCount = (int) allResults.stream()
            .filter(r -> r.status() == TransmissionDecodeResult.DecodeStatus.MISSED)
            .count();

        System.out.println(String.format("  Transmissions: %d total, %d missed",
            transmissions.size(), missedCount));
    }

    /**
     * Runs a decoder on the file and returns stats.
     */
    private LSMv2ComparisonTest.DecoderStats runDecoder(File file, boolean useV2)
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

    /**
     * Calculates the weak signal threshold based on observed energy distribution.
     */
    private void calculateWeakSignalThreshold()
    {
        if(allResults.isEmpty())
        {
            return;
        }

        // Sort by energy
        List<Float> energies = allResults.stream()
            .map(r -> r.transmission().avgEnergy())
            .sorted()
            .toList();

        // Take bottom percentile
        int thresholdIndex = (int)(energies.size() * WEAK_SIGNAL_PERCENTILE);
        thresholdIndex = Math.max(0, Math.min(thresholdIndex, energies.size() - 1));
        weakSignalThreshold = energies.get(thresholdIndex);
    }

    /**
     * Categorizes all missed transmissions by failure cause.
     */
    private void categorizeFailures()
    {
        for(TransmissionDecodeResult result : allResults)
        {
            if(result.status() == TransmissionDecodeResult.DecodeStatus.MISSED)
            {
                MissedTransmission missed = categorize(result);
                missedTransmissions.add(missed);
            }
        }
    }

    /**
     * Categorizes a single missed transmission.
     */
    private MissedTransmission categorize(TransmissionDecodeResult result)
    {
        Transmission tx = result.transmission();
        FailureCategory category;
        String diagnostic;

        // Check for incomplete (recording boundary)
        if(!tx.isComplete())
        {
            category = FailureCategory.INCOMPLETE;
            diagnostic = "Cut off at recording boundary";
        }
        // Check for too short
        else if(tx.durationMs() < TOO_SHORT_THRESHOLD_MS)
        {
            category = FailureCategory.TOO_SHORT;
            diagnostic = String.format("Duration %dms < %dms minimum",
                tx.durationMs(), TOO_SHORT_THRESHOLD_MS);
        }
        // Check for weak signal
        else if(tx.avgEnergy() < weakSignalThreshold)
        {
            category = FailureCategory.WEAK_SIGNAL;
            diagnostic = String.format("Energy %.5f < threshold %.5f",
                tx.avgEnergy(), weakSignalThreshold);
        }
        // Adequate signal but no decode = sync failure
        else if(tx.durationMs() >= TOO_SHORT_THRESHOLD_MS && tx.avgEnergy() >= weakSignalThreshold)
        {
            category = FailureCategory.SYNC_FAILURE;
            diagnostic = String.format("Adequate signal (%.5f) for %dms, no sync acquired",
                tx.avgEnergy(), tx.durationMs());
        }
        // Unknown
        else
        {
            category = FailureCategory.UNKNOWN;
            diagnostic = "Could not determine cause";
        }

        return new MissedTransmission(result, category, diagnostic);
    }

    /**
     * Identifies transmissions with low decode rates (below threshold).
     */
    private void identifyLowRateTransmissions()
    {
        for(TransmissionDecodeResult result : allResults)
        {
            LowRateTransmission lowRate =
                LowRateTransmission.createIfQualifies(result, LOW_RATE_THRESHOLD);
            if(lowRate != null)
            {
                lowRateTransmissions.add(lowRate);
            }
        }
    }

    /**
     * Prints the failure category distribution report.
     */
    private void printFailureCategoryReport()
    {
        System.out.println("=== FAILURE CATEGORY DISTRIBUTION ===");

        if(missedTransmissions.isEmpty())
        {
            System.out.println("No missed transmissions to analyze!");
            return;
        }

        // Count by category
        Map<FailureCategory, Integer> counts = new EnumMap<>(FailureCategory.class);
        for(FailureCategory cat : FailureCategory.values())
        {
            counts.put(cat, 0);
        }
        for(MissedTransmission mt : missedTransmissions)
        {
            counts.merge(mt.category(), 1, Integer::sum);
        }

        int total = missedTransmissions.size();
        int totalExpected = allResults.size();

        System.out.println();
        for(FailureCategory cat : FailureCategory.values())
        {
            int count = counts.get(cat);
            if(count > 0)
            {
                String opportunityFlag = cat.isOptimizationOpportunity() ? " *" : "";
                System.out.println(String.format("  %-20s %4d (%5.1f%%)%s",
                    cat.getDisplayName() + ":",
                    count,
                    (double) count / total * 100,
                    opportunityFlag));
            }
        }
        System.out.println("  " + "-".repeat(35));
        System.out.println(String.format("  %-20s %4d (%5.1f%% of %d expected)",
            "Total Missed:",
            total,
            (double) total / totalExpected * 100,
            totalExpected));

        // Count optimization opportunities
        long opportunities = missedTransmissions.stream()
            .filter(MissedTransmission::isOptimizationOpportunity)
            .count();

        System.out.println();
        System.out.println(String.format("  * = Optimization opportunity (%d transmissions, %.1f%% of missed)",
            opportunities,
            (double) opportunities / total * 100));

        // Print detailed list
        System.out.println();
        System.out.println("Missed Transmission Details:");
        for(MissedTransmission mt : missedTransmissions)
        {
            System.out.println("  " + mt.toSummary());
        }
    }

    /**
     * Prints the low decode rate analysis report.
     */
    private void printLowRateReport()
    {
        System.out.println("=== LOW DECODE RATE TRANSMISSIONS (<" + (int)LOW_RATE_THRESHOLD + "%) ===");

        if(lowRateTransmissions.isEmpty())
        {
            System.out.println("No low decode rate transmissions found.");
            return;
        }

        System.out.println();
        System.out.println(String.format("Count: %d", lowRateTransmissions.size()));

        // Calculate characteristics
        long totalDuration = 0;
        double totalEnergy = 0;
        int v2BetterCount = 0;

        for(LowRateTransmission lt : lowRateTransmissions)
        {
            totalDuration += lt.durationMs();
            totalEnergy += lt.avgEnergy();
            if(lt.isV2Better())
            {
                v2BetterCount++;
            }
        }

        double avgDuration = (double) totalDuration / lowRateTransmissions.size();
        double avgEnergy = totalEnergy / lowRateTransmissions.size();

        System.out.println();
        System.out.println("Characteristics:");
        System.out.println(String.format("  Average duration: %.0f ms", avgDuration));
        System.out.println(String.format("  Average energy:   %.5f", avgEnergy));
        System.out.println(String.format("  v2 better:        %d (%.1f%%)",
            v2BetterCount,
            (double) v2BetterCount / lowRateTransmissions.size() * 100));

        // Print detailed list
        System.out.println();
        System.out.println("Low Rate Transmission Details:");
        for(LowRateTransmission lt : lowRateTransmissions)
        {
            System.out.println("  " + lt.toSummary());
        }
    }

    /**
     * Prints the signal quality correlation analysis.
     */
    private void printSignalQualityCorrelation()
    {
        System.out.println("=== SIGNAL QUALITY CORRELATION ===");

        if(allResults.isEmpty())
        {
            System.out.println("No results to analyze.");
            return;
        }

        List<SignalQualityBin> bins = SignalQualityBin.createBins(allResults, SIGNAL_QUALITY_BINS);

        System.out.println();
        System.out.println(String.format("%-5s %-17s %7s %9s %9s %14s",
            "Bin", "Energy Range", "TX", "LSM %", "v2 %", "Missed"));
        System.out.println("-".repeat(65));

        for(SignalQualityBin bin : bins)
        {
            if(bin.getTotalTransmissions() > 0)
            {
                System.out.println(bin.toTableRow());
            }
        }

        // Calculate correlation
        double correlation = calculateCorrelation(bins);
        System.out.println();
        System.out.println(String.format("Correlation (energy vs v2 decode rate): %.3f", correlation));

        if(Math.abs(correlation) < 0.3)
        {
            System.out.println("  Weak or no correlation - decode success not strongly tied to signal strength");
        }
        else if(correlation > 0.3)
        {
            System.out.println("  Positive correlation - stronger signals decode better");
        }
        else
        {
            System.out.println("  Negative correlation - unexpected, may indicate issue with metrics");
        }
    }

    /**
     * Calculates Pearson correlation coefficient between bin energy and decode rate.
     */
    private double calculateCorrelation(List<SignalQualityBin> bins)
    {
        List<Double> energies = new ArrayList<>();
        List<Double> rates = new ArrayList<>();

        for(SignalQualityBin bin : bins)
        {
            if(bin.getTotalTransmissions() > 0)
            {
                energies.add((double)(bin.getMinEnergy() + bin.getMaxEnergy()) / 2);
                rates.add(bin.v2TransmissionDecodeRate());
            }
        }

        if(energies.size() < 2)
        {
            return 0;
        }

        double meanX = energies.stream().mapToDouble(d -> d).average().orElse(0);
        double meanY = rates.stream().mapToDouble(d -> d).average().orElse(0);

        double sumXY = 0, sumX2 = 0, sumY2 = 0;

        for(int i = 0; i < energies.size(); i++)
        {
            double dx = energies.get(i) - meanX;
            double dy = rates.get(i) - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }

        double denominator = Math.sqrt(sumX2 * sumY2);
        return denominator > 0 ? sumXY / denominator : 0;
    }

    /**
     * Prints optimization opportunity analysis.
     */
    private void printOptimizationOpportunities()
    {
        System.out.println("=== OPTIMIZATION OPPORTUNITIES ===");

        if(missedTransmissions.isEmpty())
        {
            System.out.println("No optimization opportunities identified.");
            return;
        }

        // Find the signal range with most optimization opportunities
        List<SignalQualityBin> bins = SignalQualityBin.createBins(allResults, SIGNAL_QUALITY_BINS);

        // Find bin with most missed transmissions that are optimization opportunities
        SignalQualityBin bestBin = null;
        int maxOpportunities = 0;

        for(SignalQualityBin bin : bins)
        {
            if(bin.getMissed() > maxOpportunities && bin.getMinEnergy() >= weakSignalThreshold)
            {
                bestBin = bin;
                maxOpportunities = bin.getMissed();
            }
        }

        if(bestBin != null && maxOpportunities > 0)
        {
            System.out.println();
            System.out.println("Optimization Zone Identified:");
            System.out.println(String.format("  Signal range:       %s", bestBin.getEnergyRangeString()));
            System.out.println(String.format("  Total transmissions: %d (%.1f%% of all)",
                bestBin.getTotalTransmissions(),
                (double) bestBin.getTotalTransmissions() / allResults.size() * 100));
            System.out.println(String.format("  Current v2 decode:   %.1f%%", bestBin.v2TransmissionDecodeRate()));
            System.out.println(String.format("  Missed in zone:      %d", bestBin.getMissed()));
            System.out.println(String.format("  Potential LDU gain:  ~%d LDUs", bestBin.potentialLduGain()));
        }

        // Count sync failures
        long syncFailures = missedTransmissions.stream()
            .filter(mt -> mt.category() == FailureCategory.SYNC_FAILURE)
            .count();

        if(syncFailures > 0)
        {
            System.out.println();
            System.out.println(String.format("Sync Failure Opportunities: %d transmissions with adequate signal", syncFailures));
            System.out.println("  These represent decoder sync acquisition failures, not signal issues.");
        }
    }

    /**
     * Prints actionable recommendations based on analysis.
     */
    private void printRecommendations()
    {
        System.out.println("=== RECOMMENDATIONS ===");
        System.out.println();

        int recNum = 1;

        // Count categories
        long syncFailures = missedTransmissions.stream()
            .filter(mt -> mt.category() == FailureCategory.SYNC_FAILURE)
            .count();

        long tooShort = missedTransmissions.stream()
            .filter(mt -> mt.category() == FailureCategory.TOO_SHORT)
            .count();

        long weakSignal = missedTransmissions.stream()
            .filter(mt -> mt.category() == FailureCategory.WEAK_SIGNAL)
            .count();

        // Recommendations based on analysis
        if(syncFailures > 0)
        {
            System.out.println(String.format("%d. Investigate %d sync acquisition failures", recNum++, syncFailures));
            System.out.println("   - These transmissions have adequate signal but no sync was acquired");
            System.out.println("   - Consider lowering sync detection threshold or improving fallback sync");
            System.out.println();
        }

        if(!lowRateTransmissions.isEmpty())
        {
            long v2BetterCount = lowRateTransmissions.stream()
                .filter(LowRateTransmission::isV2Better)
                .count();

            System.out.println(String.format("%d. Analyze %d low decode rate transmissions", recNum++, lowRateTransmissions.size()));
            System.out.println(String.format("   - v2 outperforms LSM in %d of these cases (%.1f%%)",
                v2BetterCount,
                (double) v2BetterCount / lowRateTransmissions.size() * 100));
            System.out.println("   - Focus on improving sync retention during signal fades");
            System.out.println();
        }

        if(tooShort > 0)
        {
            System.out.println(String.format("%d. %d transmissions too short (<180ms) - not decoder issues", recNum++, tooShort));
            System.out.println("   - These cannot be decoded due to P25 LDU timing constraints");
            System.out.println();
        }

        if(weakSignal > 0)
        {
            System.out.println(String.format("%d. %d weak signal transmissions - limited optimization potential", recNum++, weakSignal));
            System.out.println(String.format("   - Signal below threshold (%.5f)", weakSignalThreshold));
            System.out.println();
        }

        // Summary
        long opportunities = missedTransmissions.stream()
            .filter(MissedTransmission::isOptimizationOpportunity)
            .count();

        System.out.println(String.format("%d. Total optimization potential: %d transmissions (%.1f%% of missed)",
            recNum,
            opportunities,
            missedTransmissions.isEmpty() ? 0 : (double) opportunities / missedTransmissions.size() * 100));
    }

    /**
     * Extracts missed transmissions to separate files for manual analysis.
     */
    private void extractMissedTransmissions(List<File> sourceFiles)
    {
        if(missedTransmissions.isEmpty())
        {
            System.out.println("No missed transmissions to extract.");
            return;
        }

        System.out.println("=== EXTRACTING MISSED TRANSMISSIONS ===");
        System.out.println("Output directory: " + sExtractDir.getAbsolutePath());

        sExtractDir.mkdirs();
        TransmissionExtractor extractor = new TransmissionExtractor();

        int extracted = 0;
        for(MissedTransmission mt : missedTransmissions)
        {
            // Only extract optimization opportunities
            if(!mt.isOptimizationOpportunity())
            {
                continue;
            }

            // Find source file - for now use first file (multi-file support would require tracking)
            File sourceFile = sourceFiles.get(0);

            try
            {
                // Create descriptive filename
                String baseName = sourceFile.getName().replace(".wav", "");
                String outputName = String.format("%s_tx%03d_%s_%dms.wav",
                    baseName,
                    mt.index(),
                    mt.category().name(),
                    mt.durationMs());
                File outputFile = new File(sExtractDir, outputName);

                // Extract with 200ms buffer
                long bufferMs = 200;
                long startMs = Math.max(0, mt.transmission().startMs() - bufferMs);
                long endMs = mt.transmission().endMs() + bufferMs;

                extractor.extractSegment(sourceFile, outputFile, startMs, endMs);
                System.out.println("  Extracted: " + outputName);
                extracted++;
            }
            catch(Exception e)
            {
                System.out.println("  ERROR extracting TX#" + mt.index() + ": " + e.getMessage());
            }
        }

        System.out.println(String.format("Extracted %d optimization opportunity transmissions.", extracted));
    }
}
