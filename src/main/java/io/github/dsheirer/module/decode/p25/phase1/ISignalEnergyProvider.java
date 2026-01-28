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
 * Interface for components that can provide signal energy status.
 * Used for audio continuity decisions during decode errors to determine
 * if RF signal energy indicates an active transmission is still in progress.
 */
public interface ISignalEnergyProvider
{
    /**
     * Indicates if RF signal energy suggests an active transmission is present,
     * regardless of whether decoding is currently successful.
     *
     * @return true if signal energy indicates an active transmission
     */
    boolean isSignalPresent();

    /**
     * Returns the current normalized signal energy level.
     *
     * @return signal energy level from 0.0 (silence) to 1.0 (maximum observed energy)
     */
    float getSignalEnergyLevel();
}
