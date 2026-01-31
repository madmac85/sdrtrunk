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
 * Classification of why a transmission failed to decode.
 * Used to categorize missed transmissions for analysis.
 */
public enum FailureCategory
{
    /**
     * Transmission duration is less than one LDU (180ms).
     * Not a decoder failure - impossible to decode.
     */
    TOO_SHORT("Too Short", "Duration < 180ms (one LDU)", false),

    /**
     * Signal energy below threshold indicating weak or absent signal.
     * Not a decoder failure - signal too weak to decode.
     */
    WEAK_SIGNAL("Weak Signal", "Energy below threshold", false),

    /**
     * Adequate signal energy but decoder failed to acquire sync.
     * This is an optimization opportunity.
     */
    SYNC_FAILURE("Sync Failure", "Adequate signal, no sync acquired", true),

    /**
     * Transmission was cut off at recording boundary (start or end of file).
     * Partial data - categorize separately from complete failures.
     */
    INCOMPLETE("Incomplete", "Recording boundary cut-off", false),

    /**
     * Failure cause cannot be determined programmatically.
     * Requires manual waveform inspection.
     */
    UNKNOWN("Unknown", "Requires manual inspection", true);

    private final String displayName;
    private final String description;
    private final boolean isOptimizationOpportunity;

    FailureCategory(String displayName, String description, boolean isOptimizationOpportunity)
    {
        this.displayName = displayName;
        this.description = description;
        this.isOptimizationOpportunity = isOptimizationOpportunity;
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
     * Returns true if this failure represents an optimization opportunity.
     * TOO_SHORT and WEAK_SIGNAL are not optimization opportunities because
     * the signal is inherently undecodable.
     */
    public boolean isOptimizationOpportunity()
    {
        return isOptimizationOpportunity;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
