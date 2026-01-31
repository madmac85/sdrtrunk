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
 * Root cause categorization for sync acquisition failures.
 * Used to identify patterns and prioritize decoder improvements.
 */
public enum SyncFailureCause
{
    /**
     * Transmission starts with weak energy in the preamble window (first 100ms).
     * The decoder may miss initial sync pattern due to low signal at start.
     * Actionable: Consider lowering initial sync threshold.
     */
    WEAK_PREAMBLE("Weak Preamble", "Low energy in first 100ms despite adequate average", true),

    /**
     * Signal energy fades rapidly before sync acquisition completes.
     * The decoder detects energy but loses signal before sync lock.
     * Actionable: Improve recovery sync during energy transitions.
     */
    RAPID_FADE("Rapid Fade", "Signal fades before sync acquisition completes", true),

    /**
     * Energy is detected but sync pattern appears later than expected.
     * Possible late carrier onset or delayed preamble.
     * Actionable: Extend sync acquisition window at transmission start.
     */
    LATE_START("Late Start", "Energy detected but sync pattern delayed", true),

    /**
     * Adequate average energy but high variance indicates noisy or interfered signal.
     * Signal instability prevents reliable sync detection.
     * Actionable: Consider noise-tolerant sync algorithms.
     */
    NOISE_INTERFERENCE("Noise/Interference", "High variance despite adequate energy", false),

    /**
     * Symbol timing appears to drift or have jitter.
     * Visible in signal characteristics but requires manual inspection to confirm.
     * Actionable: Investigate timing recovery improvements.
     */
    TIMING_DRIFT("Timing Drift", "Symbol timing issues suspected", false),

    /**
     * No clear pattern identified from signal analysis.
     * Requires manual waveform inspection.
     * Not immediately actionable.
     */
    INDETERMINATE("Indeterminate", "No clear pattern - requires manual inspection", false);

    private final String displayName;
    private final String description;
    private final boolean highPriority;

    SyncFailureCause(String displayName, String description, boolean highPriority)
    {
        this.displayName = displayName;
        this.description = description;
        this.highPriority = highPriority;
    }

    /**
     * Human-readable name for reports.
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Detailed description of the failure cause.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Returns true if this cause is high priority for decoder improvements.
     * High priority causes have clear patterns and actionable fixes.
     */
    public boolean isHighPriority()
    {
        return highPriority;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
