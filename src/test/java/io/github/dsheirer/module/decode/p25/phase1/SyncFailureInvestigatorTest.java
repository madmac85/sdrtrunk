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

import org.junit.jupiter.api.Test;

/**
 * Test wrapper for running SyncFailureInvestigator on the sample files.
 */
public class SyncFailureInvestigatorTest
{
    private static final String SAMPLES_DIR = "/home/kdolan/GitHub/sdrtrunk/_SAMPLES";

    @Test
    public void runInvestigation()
    {
        // Find the samples directory
        java.io.File samplesDir = new java.io.File(SAMPLES_DIR);
        if(!samplesDir.exists() || !samplesDir.isDirectory())
        {
            System.out.println("Samples directory not found: " + samplesDir.getAbsolutePath());
            System.out.println("Skipping test - no sample files available");
            return;
        }

        System.out.println("Running investigation on: " + samplesDir.getAbsolutePath());

        // Run the investigator
        SyncFailureInvestigator.main(new String[]{"--dir", samplesDir.getAbsolutePath()});
    }
}
