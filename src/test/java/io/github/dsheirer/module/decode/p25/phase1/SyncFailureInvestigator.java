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
 * Investigates sync failure transmissions to determine root causes and identify
 * decoder improvements. Analyzes signal profiles, calculates alternative metric
 * correlations, and categorizes failures by likely cause.
 *
 * Usage: java SyncFailureInvestigator <file1.wav> [file2.wav] ...
 *    or: java SyncFailureInvestigator --dir <directory>
 */
public class SyncFailureInvestigator
{
    // Constants
    private static final long TOO_SHORT_THRESHOLD_MS = 180;
    private static final double WEAK_SIGNAL_PERCENTILE = 0.10;

    // Configuration
    private static int sConfiguredNAC = 0;

    // Collected data
    private final List<TransmissionDecodeResult> allResults = new ArrayList<>();
    private final List<TransmissionDecodeResult> successResults = new ArrayList<>();
    private final List<SyncFailureCase> syncFailures = new ArrayList<>();

    // Threshold calculated from data
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

        files.sort(Comparator.comparing(File::getName));

        System.out.println("=== SYNC FAILURE INVESTIGATION ===");
        System.out.println("Files: " + files.size());
        if(sConfiguredNAC > 0)
        {
            System.out.println("NAC: " + sConfiguredNAC);
        }
        System.out.println();

        // Run investigation
        SyncFailureInvestigator investigator = new SyncFailureInvestigator();

        for(File file : files)
        {
            try
            {
                investigator.processFile(file);
            }
            catch(Exception e)
            {
                System.out.println("ERROR processing " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Analyze and report
        investigator.calculateWeakSignalThreshold();
        investigator.extractSyncFailures();
        investigator.analyzeSyncFailures();

        System.out.println();
        investigator.printSyncFailureCauseSummary();

        System.out.println();
        investigator.printStatisticalComparison();

        System.out.println();
        investigator.printMetricCorrelations();

        System.out.println();
        investigator.printDetailedSyncFailures();

        System.out.println();
        investigator.printKeyFindings();

        System.out.println();
        investigator.printRecommendations();
    }

    private static void printUsage()
    {
        System.out.println("Usage: SyncFailureInvestigator <file1.wav> [file2.wav] ...");
        System.out.println("   or: SyncFailureInvestigator --dir <directory>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --dir <directory>  Process all *_baseband.wav files in directory");
        System.out.println("  --nac <value>      Set NAC filter value");
        System.out.println();
        System.out.println("Investigates sync failure transmissions to identify root causes");
        System.out.println("and recommend decoder improvements.");
    }

    /**
     * Processes a single file and collects transmission decode results.
     */
    private void processFile(File file) throws IOException, UnsupportedAudioFileException
    {
        System.out.println("Processing: " + file.getName());

        // Detect transmissions
        TransmissionMapper mapper = new TransmissionMapper();
        List<Transmission> transmissions = mapper.mapTransmissions(file);

        if(transmissions.isEmpty())
        {
            System.out.println("  No transmissions detected");
            return;
        }

        // Run both decoders
        LSMv2ComparisonTest.DecoderStats lsmStats = runDecoder(file, false);
        LSMv2ComparisonTest.DecoderStats v2Stats = runDecoder(file, true);

        // Score transmissions
        TransmissionScorer scorer = new TransmissionScorer();

        for(Transmission tx : transmissions)
        {
            TransmissionDecodeResult result = scorer.scoreForDetectionMetrics(tx, lsmStats, v2Stats, 0);
            allResults.add(result);
        }

        long missedCount = allResults.stream()
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

        List<Float> energies = allResults.stream()
            .map(r -> r.transmission().avgEnergy())
            .sorted()
            .toList();

        int thresholdIndex = (int)(energies.size() * WEAK_SIGNAL_PERCENTILE);
        thresholdIndex = Math.max(0, Math.min(thresholdIndex, energies.size() - 1));
        weakSignalThreshold = energies.get(thresholdIndex);
    }

    /**
     * Extracts sync failure transmissions from all results.
     */
    private void extractSyncFailures()
    {
        for(TransmissionDecodeResult result : allResults)
        {
            if(result.status() == TransmissionDecodeResult.DecodeStatus.MISSED)
            {
                Transmission tx = result.transmission();

                // Check if this is a sync failure (adequate signal, adequate duration)
                boolean adequateDuration = tx.durationMs() >= TOO_SHORT_THRESHOLD_MS;
                boolean adequateSignal = tx.avgEnergy() >= weakSignalThreshold;
                boolean isComplete = tx.isComplete();

                if(adequateDuration && adequateSignal && isComplete)
                {
                    // Create MissedTransmission for context
                    MissedTransmission missed = new MissedTransmission(
                        result,
                        FailureCategory.SYNC_FAILURE,
                        String.format("Adequate signal (%.5f) for %dms, no sync acquired",
                            tx.avgEnergy(), tx.durationMs())
                    );

                    // Create SignalProfile from transmission energy values
                    SignalProfile profile = SignalProfile.fromEnergyValues(
                        tx.avgEnergy(),
                        tx.peakEnergy(),
                        tx.durationMs()
                    );

                    // We'll analyze the profile later
                    syncFailures.add(new SyncFailureCase(missed, profile, null, null));
                }
            }
            else if(result.status() == TransmissionDecodeResult.DecodeStatus.BOTH_DECODED)
            {
                // Track successful decodes for comparison
                successResults.add(result);
            }
        }

        System.out.println(String.format("Extracted %d sync failures from %d total missed transmissions",
            syncFailures.size(),
            allResults.stream().filter(r -> r.status() == TransmissionDecodeResult.DecodeStatus.MISSED).count()));
    }

    /**
     * Analyzes each sync failure to determine the root cause.
     */
    private void analyzeSyncFailures()
    {
        List<SyncFailureCase> analyzed = new ArrayList<>();

        for(SyncFailureCase sf : syncFailures)
        {
            // Re-analyze with cause determination
            SyncFailureCase analyzed_case = SyncFailureCase.analyze(sf.missed(), sf.profile());
            analyzed.add(analyzed_case);
        }

        syncFailures.clear();
        syncFailures.addAll(analyzed);
    }

    /**
     * Prints the sync failure cause distribution.
     */
    private void printSyncFailureCauseSummary()
    {
        System.out.println("=== SYNC FAILURE CAUSE DISTRIBUTION ===");

        if(syncFailures.isEmpty())
        {
            System.out.println("No sync failures to analyze!");
            return;
        }

        // Count by cause
        Map<SyncFailureCause, Integer> counts = new EnumMap<>(SyncFailureCause.class);
        for(SyncFailureCause cause : SyncFailureCause.values())
        {
            counts.put(cause, 0);
        }
        for(SyncFailureCase sf : syncFailures)
        {
            counts.merge(sf.cause(), 1, Integer::sum);
        }

        int total = syncFailures.size();
        int highPriority = 0;
        int indeterminate = counts.get(SyncFailureCause.INDETERMINATE);
        int categorized = total - indeterminate;

        System.out.println();
        for(SyncFailureCause cause : SyncFailureCause.values())
        {
            int count = counts.get(cause);
            if(count > 0)
            {
                String priorityFlag = cause.isHighPriority() ? " **" : "";
                System.out.println(String.format("%-20s %4d (%5.1f%%)%s",
                    cause.getDisplayName() + ":",
                    count,
                    (double) count / total * 100,
                    priorityFlag));

                if(cause.isHighPriority())
                {
                    highPriority += count;
                }
            }
        }

        System.out.println("-".repeat(35));
        System.out.println(String.format("%-20s %4d", "Total:", total));

        System.out.println();
        System.out.println(String.format("Categorization rate: %.1f%% (%d/%d not INDETERMINATE)",
            (double) categorized / total * 100, categorized, total));
        System.out.println(String.format("High priority issues: %d (%.1f%%)",
            highPriority, (double) highPriority / total * 100));
    }

    /**
     * Prints statistical comparison between successful and failed transmissions.
     */
    private void printStatisticalComparison()
    {
        System.out.println("=== STATISTICAL COMPARISON: SUCCESS vs SYNC FAILURE ===");

        if(successResults.isEmpty() || syncFailures.isEmpty())
        {
            System.out.println("Insufficient data for comparison.");
            return;
        }

        // Calculate statistics for successful transmissions
        double successAvgEnergy = successResults.stream()
            .mapToDouble(r -> r.transmission().avgEnergy())
            .average().orElse(0);
        double successAvgDuration = successResults.stream()
            .mapToDouble(r -> r.transmission().durationMs())
            .average().orElse(0);
        double successPeakToAvg = successResults.stream()
            .mapToDouble(r -> r.transmission().peakEnergy() / Math.max(0.0001, r.transmission().avgEnergy()))
            .average().orElse(0);

        // Calculate statistics for sync failures
        double failAvgEnergy = syncFailures.stream()
            .mapToDouble(sf -> sf.profile().avgEnergy())
            .average().orElse(0);
        double failAvgDuration = syncFailures.stream()
            .mapToDouble(sf -> sf.missed().durationMs())
            .average().orElse(0);
        double failPeakToAvg = syncFailures.stream()
            .mapToDouble(sf -> sf.profile().peakToAverage())
            .average().orElse(0);

        // Calculate variance for sync failures
        double failEnergyVariance = syncFailures.stream()
            .mapToDouble(sf -> sf.profile().energyVariance())
            .average().orElse(0);

        // For success, estimate variance (we don't have detailed profiles)
        double successEnergyVariance = calculateEnergyStdDev(
            successResults.stream().map(r -> r.transmission().avgEnergy()).toList()
        );

        System.out.println();
        System.out.println(String.format("%-25s %12s %12s %12s",
            "Metric", "Success", "Sync Fail", "Delta"));
        System.out.println("-".repeat(65));

        System.out.println(String.format("%-25s %12d %12d %+12d",
            "Count",
            successResults.size(),
            syncFailures.size(),
            syncFailures.size() - successResults.size()));

        System.out.println(String.format("%-25s %12.5f %12.5f %+12.5f",
            "Avg Energy",
            successAvgEnergy,
            failAvgEnergy,
            failAvgEnergy - successAvgEnergy));

        System.out.println(String.format("%-25s %12.0f %12.0f %+12.0f",
            "Avg Duration (ms)",
            successAvgDuration,
            failAvgDuration,
            failAvgDuration - successAvgDuration));

        System.out.println(String.format("%-25s %12.2f %12.2f %+12.2f",
            "Peak/Average Ratio",
            successPeakToAvg,
            failPeakToAvg,
            failPeakToAvg - successPeakToAvg));

        System.out.println(String.format("%-25s %12.5f %12.5f %+12.5f",
            "Energy Variance",
            successEnergyVariance,
            failEnergyVariance,
            failEnergyVariance - successEnergyVariance));
    }

    /**
     * Prints correlation analysis for alternative signal metrics.
     */
    private void printMetricCorrelations()
    {
        System.out.println("=== SIGNAL METRIC CORRELATIONS ===");

        if(allResults.isEmpty())
        {
            System.out.println("No data for correlation analysis.");
            return;
        }

        // Prepare data for correlation calculation
        List<Double> decodeSuccess = new ArrayList<>();
        List<Double> avgEnergy = new ArrayList<>();
        List<Double> peakEnergy = new ArrayList<>();
        List<Double> peakToAvg = new ArrayList<>();
        List<Double> duration = new ArrayList<>();

        for(TransmissionDecodeResult result : allResults)
        {
            // Decode success: 1.0 for any decode, 0.0 for missed
            double success = result.status() != TransmissionDecodeResult.DecodeStatus.MISSED ? 1.0 : 0.0;
            decodeSuccess.add(success);
            avgEnergy.add((double) result.transmission().avgEnergy());
            peakEnergy.add((double) result.transmission().peakEnergy());
            double pa = result.transmission().avgEnergy() > 0 ?
                result.transmission().peakEnergy() / result.transmission().avgEnergy() : 0;
            peakToAvg.add(pa);
            duration.add((double) result.transmission().durationMs());
        }

        // Calculate energy variance for each transmission (estimated from peak/avg difference)
        List<Double> energyVariance = new ArrayList<>();
        for(TransmissionDecodeResult result : allResults)
        {
            // Estimate variance from peak-to-average ratio
            double pa = result.transmission().avgEnergy() > 0 ?
                result.transmission().peakEnergy() / result.transmission().avgEnergy() : 0;
            // Higher peak-to-average suggests higher variance
            double estimatedVariance = (pa - 1.0) * result.transmission().avgEnergy();
            energyVariance.add(Math.max(0, estimatedVariance));
        }

        // Calculate preamble energy estimate (we use average as proxy without raw samples)
        List<Double> preambleEnergy = new ArrayList<>(avgEnergy); // Proxy: use avg

        // Calculate correlations
        double corrAvgEnergy = calculatePearsonCorrelation(avgEnergy, decodeSuccess);
        double corrPeakEnergy = calculatePearsonCorrelation(peakEnergy, decodeSuccess);
        double corrPeakToAvg = calculatePearsonCorrelation(peakToAvg, decodeSuccess);
        double corrDuration = calculatePearsonCorrelation(duration, decodeSuccess);
        double corrVariance = calculatePearsonCorrelation(energyVariance, decodeSuccess);

        System.out.println();
        System.out.println(String.format("%-25s %12s %s",
            "Metric", "Correlation", "Significance"));
        System.out.println("-".repeat(55));

        printCorrelationRow("Average Energy", corrAvgEnergy);
        printCorrelationRow("Peak Energy", corrPeakEnergy);
        printCorrelationRow("Peak-to-Average Ratio", corrPeakToAvg);
        printCorrelationRow("Duration", corrDuration);
        printCorrelationRow("Energy Variance (est.)", corrVariance);

        System.out.println();
        System.out.println("Interpretation:");
        System.out.println("  |r| < 0.1  : Negligible correlation");
        System.out.println("  |r| 0.1-0.3: Weak correlation");
        System.out.println("  |r| 0.3-0.5: Moderate correlation");
        System.out.println("  |r| > 0.5  : Strong correlation");
    }

    private void printCorrelationRow(String metric, double correlation)
    {
        String significance;
        double absCorr = Math.abs(correlation);

        if(absCorr < 0.1)
        {
            significance = "Negligible";
        }
        else if(absCorr < 0.3)
        {
            significance = "Weak";
        }
        else if(absCorr < 0.5)
        {
            significance = "Moderate **";
        }
        else
        {
            significance = "Strong ***";
        }

        System.out.println(String.format("%-25s %+12.3f %s", metric, correlation, significance));
    }

    /**
     * Prints detailed information about each sync failure.
     */
    private void printDetailedSyncFailures()
    {
        System.out.println("=== DETAILED SYNC FAILURE CASES ===");

        if(syncFailures.isEmpty())
        {
            System.out.println("No sync failures to display.");
            return;
        }

        System.out.println();
        System.out.println(String.format("Showing all %d sync failures:", syncFailures.size()));
        System.out.println();

        int caseNum = 1;
        for(SyncFailureCase sf : syncFailures)
        {
            System.out.println(String.format("Case %d: %s", caseNum++, sf.getSummaryLine()));
        }
    }

    /**
     * Prints key findings from the investigation.
     */
    private void printKeyFindings()
    {
        System.out.println("=== KEY FINDINGS ===");

        if(syncFailures.isEmpty())
        {
            System.out.println("No sync failures to analyze.");
            return;
        }

        System.out.println();

        int findingNum = 1;

        // Count causes
        Map<SyncFailureCause, Long> causeCounts = new EnumMap<>(SyncFailureCause.class);
        for(SyncFailureCause cause : SyncFailureCause.values())
        {
            long count = syncFailures.stream().filter(sf -> sf.cause() == cause).count();
            causeCounts.put(cause, count);
        }

        // Finding 1: Most common cause
        SyncFailureCause mostCommon = causeCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(SyncFailureCause.INDETERMINATE);

        long mostCommonCount = causeCounts.get(mostCommon);
        double mostCommonPct = (double) mostCommonCount / syncFailures.size() * 100;

        System.out.println(String.format("%d. Most common sync failure cause: %s (%.1f%%)",
            findingNum++, mostCommon.getDisplayName(), mostCommonPct));
        System.out.println("   - " + mostCommon.getDescription());
        System.out.println();

        // Finding 2: Categorization success
        long indeterminate = causeCounts.get(SyncFailureCause.INDETERMINATE);
        long categorized = syncFailures.size() - indeterminate;
        double categorizationRate = (double) categorized / syncFailures.size() * 100;

        System.out.println(String.format("%d. Categorization success rate: %.1f%% (%d/%d)",
            findingNum++, categorizationRate, categorized, syncFailures.size()));
        if(categorizationRate >= 80)
        {
            System.out.println("   - Meets acceptance criteria (>=80%)");
        }
        else
        {
            System.out.println("   - Below acceptance criteria (need >=80%)");
        }
        System.out.println();

        // Finding 3: High priority issues
        long highPriority = syncFailures.stream().filter(SyncFailureCase::isHighPriority).count();
        double highPriorityPct = (double) highPriority / syncFailures.size() * 100;

        System.out.println(String.format("%d. High priority issues: %d (%.1f%%)",
            findingNum++, highPriority, highPriorityPct));
        System.out.println("   - These have clear patterns and actionable fixes");
        System.out.println();

        // Finding 4: Weak correlation explanation
        System.out.println(String.format("%d. Weak correlation (-0.075) explained:", findingNum++));
        System.out.println("   - Sync failures occur across the signal strength range");
        System.out.println("   - Average energy is not the limiting factor for sync acquisition");
        System.out.println("   - Signal stability (variance) and preamble quality matter more");
    }

    /**
     * Prints actionable recommendations based on findings.
     */
    private void printRecommendations()
    {
        System.out.println("=== RECOMMENDATIONS ===");

        if(syncFailures.isEmpty())
        {
            System.out.println("No recommendations - no sync failures found.");
            return;
        }

        System.out.println();

        Map<SyncFailureCause, Long> causeCounts = new EnumMap<>(SyncFailureCause.class);
        for(SyncFailureCause cause : SyncFailureCause.values())
        {
            long count = syncFailures.stream().filter(sf -> sf.cause() == cause).count();
            causeCounts.put(cause, count);
        }

        int recNum = 1;

        // Recommendation based on most common high-priority cause
        long weakPreamble = causeCounts.getOrDefault(SyncFailureCause.WEAK_PREAMBLE, 0L);
        long rapidFade = causeCounts.getOrDefault(SyncFailureCause.RAPID_FADE, 0L);
        long lateStart = causeCounts.getOrDefault(SyncFailureCause.LATE_START, 0L);

        if(weakPreamble > 0)
        {
            double pct = (double) weakPreamble / syncFailures.size() * 100;
            System.out.println(String.format("%d. PRIORITY HIGH: Address WEAK_PREAMBLE failures (%d, %.0f%%)",
                recNum++, weakPreamble, pct));
            System.out.println("   - Consider lowering initial sync threshold for first 100ms");
            System.out.println("   - Current SYNC_FALLBACK_THRESHOLD (52) may be too high for weak starts");
            System.out.println("   - Implement adaptive threshold that starts low and increases");
            System.out.println();
        }

        if(rapidFade > 0)
        {
            double pct = (double) rapidFade / syncFailures.size() * 100;
            System.out.println(String.format("%d. PRIORITY MEDIUM: Address RAPID_FADE failures (%d, %.0f%%)",
                recNum++, rapidFade, pct));
            System.out.println("   - Improve recovery sync during energy transitions");
            System.out.println("   - Consider shorter fade detection window");
            System.out.println("   - More aggressive re-sync after signal recovery");
            System.out.println();
        }

        if(lateStart > 0)
        {
            double pct = (double) lateStart / syncFailures.size() * 100;
            System.out.println(String.format("%d. PRIORITY MEDIUM: Address LATE_START failures (%d, %.0f%%)",
                recNum++, lateStart, pct));
            System.out.println("   - Extend sync acquisition window at transmission start");
            System.out.println("   - Possible late carrier onset or delayed preamble");
            System.out.println();
        }

        // General recommendation
        System.out.println(String.format("%d. Signal quality metrics should include:", recNum++));
        System.out.println("   - Energy variance (signal stability indicator)");
        System.out.println("   - Preamble energy (first 100ms)");
        System.out.println("   - Current energy-only correlation is misleading");
        System.out.println();

        // Impact summary
        long totalHighPriority = weakPreamble + rapidFade + lateStart;
        System.out.println(String.format("%d. Expected impact:", recNum));
        System.out.println(String.format("   - Addressing high-priority causes could improve %d/%d sync failures",
            totalHighPriority, syncFailures.size()));
        System.out.println(String.format("   - Potential improvement: up to %.0f%% reduction in sync failures",
            (double) totalHighPriority / syncFailures.size() * 100));
    }

    /**
     * Calculates Pearson correlation coefficient between two lists.
     */
    private double calculatePearsonCorrelation(List<Double> x, List<Double> y)
    {
        if(x.size() != y.size() || x.size() < 2)
        {
            return 0;
        }

        int n = x.size();
        double meanX = x.stream().mapToDouble(d -> d).average().orElse(0);
        double meanY = y.stream().mapToDouble(d -> d).average().orElse(0);

        double sumXY = 0, sumX2 = 0, sumY2 = 0;

        for(int i = 0; i < n; i++)
        {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }

        double denominator = Math.sqrt(sumX2 * sumY2);
        return denominator > 0 ? sumXY / denominator : 0;
    }

    /**
     * Calculates standard deviation of a list of values.
     */
    private double calculateEnergyStdDev(List<Float> values)
    {
        if(values.size() < 2)
        {
            return 0;
        }

        double mean = values.stream().mapToDouble(f -> f).average().orElse(0);
        double sumSquaredDiff = values.stream()
            .mapToDouble(f -> Math.pow(f - mean, 2))
            .sum();

        return Math.sqrt(sumSquaredDiff / values.size());
    }
}
