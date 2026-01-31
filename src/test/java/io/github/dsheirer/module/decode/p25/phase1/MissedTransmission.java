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
 * A transmission where both decoders recovered zero LDUs,
 * with failure categorization and diagnostic information.
 */
public record MissedTransmission(
    TransmissionDecodeResult result,
    FailureCategory category,
    String diagnosticInfo
)
{
    /**
     * The underlying transmission data.
     */
    public Transmission transmission()
    {
        return result.transmission();
    }

    /**
     * Transmission index in the file.
     */
    public int index()
    {
        return result.transmission().index();
    }

    /**
     * Duration in milliseconds.
     */
    public long durationMs()
    {
        return result.transmission().durationMs();
    }

    /**
     * Average signal energy.
     */
    public float avgEnergy()
    {
        return result.transmission().avgEnergy();
    }

    /**
     * Peak signal energy.
     */
    public float peakEnergy()
    {
        return result.transmission().peakEnergy();
    }

    /**
     * Whether the transmission is complete (not cut off at recording boundary).
     */
    public boolean isComplete()
    {
        return result.transmission().isComplete();
    }

    /**
     * Whether this failure represents an optimization opportunity.
     */
    public boolean isOptimizationOpportunity()
    {
        return category.isOptimizationOpportunity();
    }

    /**
     * Returns a summary string for display.
     */
    public String toSummary()
    {
        return String.format("TX#%d: %s - %dms, energy=%.4f [%s]",
            index(),
            category.getDisplayName(),
            durationMs(),
            avgEnergy(),
            diagnosticInfo);
    }

    /**
     * Returns a detailed diagnostic string.
     */
    public String toDetailedReport()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transmission #%d%n", index()));
        sb.append(String.format("  Category: %s (%s)%n", category.getDisplayName(), category.getDescription()));
        sb.append(String.format("  Duration: %d ms%n", durationMs()));
        sb.append(String.format("  Time Window: %d - %d ms%n",
            result.transmission().startMs(), result.transmission().endMs()));
        sb.append(String.format("  Signal: avg=%.5f, peak=%.5f%n", avgEnergy(), peakEnergy()));
        sb.append(String.format("  Complete: %s%n", isComplete() ? "Yes" : "No (boundary cut-off)"));
        sb.append(String.format("  Optimization Opportunity: %s%n", isOptimizationOpportunity() ? "Yes" : "No"));
        if(!diagnosticInfo.isEmpty())
        {
            sb.append(String.format("  Diagnostic: %s%n", diagnosticInfo));
        }
        return sb.toString();
    }
}
