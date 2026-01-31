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
 * Represents a sync failure case with detailed analysis results.
 * Wraps a MissedTransmission with signal profile and root cause determination.
 */
public record SyncFailureCase(
    MissedTransmission missed,      // The original missed transmission
    SignalProfile profile,           // Detailed signal characterization
    SyncFailureCause cause,          // Determined root cause
    String evidence                  // Diagnostic explanation for the cause
)
{
    /**
     * Analyzes a MissedTransmission and determines the likely sync failure cause.
     *
     * @param missed The missed transmission to analyze
     * @param profile The signal profile for this transmission
     * @return SyncFailureCase with cause determination
     */
    public static SyncFailureCase analyze(MissedTransmission missed, SignalProfile profile)
    {
        SyncFailureCause cause;
        String evidence;

        // When we have detailed sample data, use profile-based detection
        if(profile.hasWeakPreamble())
        {
            cause = SyncFailureCause.WEAK_PREAMBLE;
            float preamblePercent = profile.avgEnergy() > 0 ?
                (profile.preambleEnergy() / profile.avgEnergy()) * 100 : 0;
            evidence = String.format("Preamble energy at %.0f%% of average (threshold: 70%%)", preamblePercent);
        }
        else if(profile.hasRapidFade())
        {
            cause = SyncFailureCause.RAPID_FADE;
            evidence = String.format("Energy slope %.1f dB/100ms (threshold: -2.0 dB/100ms)", profile.energySlope());
        }
        else if(profile.hasLateStart())
        {
            cause = SyncFailureCause.LATE_START;
            evidence = String.format("Rising energy slope %.1f dB/100ms with weak initial energy", profile.energySlope());
        }
        else if(profile.hasHighVariance())
        {
            cause = SyncFailureCause.NOISE_INTERFERENCE;
            float variancePercent = profile.avgEnergy() > 0 ?
                (profile.energyVariance() / profile.avgEnergy()) * 100 : 0;
            evidence = String.format("Energy variance at %.0f%% of average (threshold: 30%%)", variancePercent);
        }
        // Fallback to heuristic analysis using available metrics
        else
        {
            cause = analyzeWithHeuristics(missed, profile);
            evidence = getHeuristicEvidence(missed, profile, cause);
        }

        return new SyncFailureCase(missed, profile, cause, evidence);
    }

    /**
     * Heuristic analysis when detailed signal profile data is not available.
     * Uses peak-to-average ratio, duration, and transmission characteristics.
     */
    private static SyncFailureCause analyzeWithHeuristics(MissedTransmission missed, SignalProfile profile)
    {
        Transmission tx = missed.result().transmission();
        float peakToAvg = profile.peakToAverage();
        long durationMs = tx.durationMs();

        // High peak-to-average ratio (>3.0) suggests signal instability - possible fading
        if(peakToAvg > 3.0f)
        {
            // Very high ratio (>5.0) with short duration suggests rapid fade
            if(peakToAvg > 5.0f || durationMs < 2000)
            {
                return SyncFailureCause.RAPID_FADE;
            }
            // Moderate high ratio suggests noise/interference
            return SyncFailureCause.NOISE_INTERFERENCE;
        }

        // Short transmissions (1-2 LDUs worth) with normal peak ratio suggest weak preamble
        // The decoder may miss the initial sync window
        if(durationMs < 500)
        {
            return SyncFailureCause.WEAK_PREAMBLE;
        }

        // Transmissions starting at time 0 (recording boundary) suggest late start
        // or weak preamble at transmission beginning
        if(tx.startMs() == 0)
        {
            return SyncFailureCause.WEAK_PREAMBLE;
        }

        // Longer transmissions (>5s) with normal characteristics suggest timing drift
        if(durationMs > 5000 && peakToAvg > 1.5f && peakToAvg <= 3.0f)
        {
            return SyncFailureCause.TIMING_DRIFT;
        }

        // Medium duration with relatively stable signal - likely weak preamble
        if(peakToAvg <= 1.5f)
        {
            return SyncFailureCause.WEAK_PREAMBLE;
        }

        // Default: indeterminate if no heuristic matches
        return SyncFailureCause.INDETERMINATE;
    }

    /**
     * Generates evidence string for heuristic-based categorization.
     */
    private static String getHeuristicEvidence(MissedTransmission missed, SignalProfile profile, SyncFailureCause cause)
    {
        Transmission tx = missed.result().transmission();
        float peakToAvg = profile.peakToAverage();

        return switch(cause) {
            case WEAK_PREAMBLE -> String.format(
                "Heuristic: duration=%dms, peak/avg=%.2f suggests weak start (transmission start at %dms)",
                tx.durationMs(), peakToAvg, tx.startMs());
            case RAPID_FADE -> String.format(
                "Heuristic: high peak/avg ratio %.2f indicates signal instability/fading",
                peakToAvg);
            case NOISE_INTERFERENCE -> String.format(
                "Heuristic: peak/avg=%.2f suggests noise or interference patterns",
                peakToAvg);
            case TIMING_DRIFT -> String.format(
                "Heuristic: long duration %dms with moderate variation (peak/avg=%.2f) suggests timing drift",
                tx.durationMs(), peakToAvg);
            case LATE_START -> String.format(
                "Heuristic: transmission characteristics suggest delayed sync pattern");
            case INDETERMINATE -> String.format(
                "No clear pattern - peak/avg=%.2f, duration=%dms, energy=%.6f",
                peakToAvg, tx.durationMs(), profile.avgEnergy());
        };
    }

    /**
     * Returns true if this cause is high priority for decoder improvements.
     */
    public boolean isHighPriority()
    {
        return cause.isHighPriority();
    }

    /**
     * Returns a formatted summary line for reports.
     */
    public String getSummaryLine()
    {
        var tx = missed.result().transmission();
        return String.format("[%s] TX@%.3fs dur=%dms energy=%.6f peak/avg=%.2f - %s",
            cause.getDisplayName(),
            tx.startMs() / 1000.0,
            tx.durationMs(),
            profile.avgEnergy(),
            profile.peakToAverage(),
            evidence);
    }
}
