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

import io.github.dsheirer.dsp.symbol.Dibit;

/**
 * Listener interface for training sequence notifications from the message framer.
 * When a NID is successfully decoded with a known (configured) NAC, the framer notifies
 * registered listeners with the known NAC dibit sequence so that upstream components
 * (e.g. the CMA equalizer) can use supervised LMS training instead of blind CMA adaptation.
 */
public interface ITrainingSequenceListener
{
    /**
     * Called when known NAC dibits are available from a successfully decoded NID.
     * The dibits represent the NAC portion (first 6 data dibits) of the NID, derived from the
     * configured NAC value. These can be used as training references for adaptive equalization.
     *
     * @param knownDibits array of known NAC dibits (6 dibits = 12 NAC bits)
     * @param count number of valid dibits in the array
     */
    void trainingDibitsAvailable(Dibit[] knownDibits, int count);
}
