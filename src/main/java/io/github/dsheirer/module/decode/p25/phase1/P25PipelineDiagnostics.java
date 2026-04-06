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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight P25 pipeline diagnostics. Writes structured events to a dedicated log file
 * when enabled via system property -Dp25.diag=true. Zero cost when disabled.
 *
 * Each line: timestamp | channel | stage | event | detail
 *
 * Enable: add -Dp25.diag=true to JVM args (or set in SDRTrunk system properties)
 * Output: p25-pipeline-diag.log in the current working directory
 */
public class P25PipelineDiagnostics
{
    private static final boolean ENABLED = Boolean.getBoolean("p25.diag");
    private static final String DIAG_FILE = System.getProperty("p25.diag.file", "p25-pipeline-diag.log");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());
    private static PrintWriter sWriter;

    static
    {
        if(ENABLED)
        {
            try
            {
                sWriter = new PrintWriter(new FileWriter(DIAG_FILE, true), true);
                sWriter.println("--- P25 Pipeline Diagnostics started at " + Instant.now() + " ---");
            }
            catch(IOException e)
            {
                System.err.println("P25 diag: failed to open " + DIAG_FILE + ": " + e.getMessage());
            }
        }
    }

    public static boolean isEnabled()
    {
        return ENABLED && sWriter != null;
    }

    public static void log(String channel, String stage, String event, String detail)
    {
        if(ENABLED && sWriter != null)
        {
            sWriter.printf("%s | %-20s | %-12s | %-24s | %s%n",
                TIME_FMT.format(Instant.now()), channel, stage, event, detail);
        }
    }

    public static void log(String channel, String stage, String event)
    {
        log(channel, stage, event, "");
    }
}
