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
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Comparison test that plays a baseband recording through both LSM and LSM v2 decoders
 * and reports the decode statistics for each. Run with a path to a baseband WAV file.
 *
 * Usage: java LSMv2ComparisonTest <path-to-baseband.wav>
 */
public class LSMv2ComparisonTest
{
    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: LSMv2ComparisonTest <path-to-baseband.wav>");
            System.out.println("  Plays the baseband recording through both LSM and LSM v2 decoders");
            System.out.println("  and reports decode statistics for comparison.");
            return;
        }

        String filePath = args[0];
        File file = new File(filePath);

        if(!file.exists())
        {
            System.out.println("ERROR: File not found: " + filePath);
            return;
        }

        System.out.println("=== P25 LSM vs LSM v2 Comparison ===");
        System.out.println("File: " + file.getName());
        System.out.println();

        // Run LSM (original)
        System.out.println("--- Running LSM (original) ---");
        DecoderStats lsmStats = runDecoder(file, false);

        // Run LSM v2
        System.out.println();
        System.out.println("--- Running LSM v2 (cold-start improvements) ---");
        DecoderStats lsmV2Stats = runDecoder(file, true);

        // Print comparison
        System.out.println();
        System.out.println("=== COMPARISON RESULTS ===");
        System.out.println(String.format("%-25s %10s %10s %10s", "", "LSM", "LSM v2", "Delta"));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Valid Messages",
                lsmStats.validMessages, lsmV2Stats.validMessages,
                lsmV2Stats.validMessages - lsmStats.validMessages));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Total Messages",
                lsmStats.totalMessages, lsmV2Stats.totalMessages,
                lsmV2Stats.totalMessages - lsmStats.totalMessages));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Sync Losses",
                lsmStats.syncLosses, lsmV2Stats.syncLosses,
                lsmV2Stats.syncLosses - lsmStats.syncLosses));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Bit Errors",
                lsmStats.bitErrors, lsmV2Stats.bitErrors,
                lsmV2Stats.bitErrors - lsmStats.bitErrors));

        double lsmRate = lsmStats.totalMessages > 0 ?
                (double)lsmStats.validMessages / lsmStats.totalMessages * 100.0 : 0;
        double v2Rate = lsmV2Stats.totalMessages > 0 ?
                (double)lsmV2Stats.validMessages / lsmV2Stats.totalMessages * 100.0 : 0;
        System.out.println(String.format("%-25s %9.1f%% %9.1f%% %+9.1f%%", "Valid Rate",
                lsmRate, v2Rate, v2Rate - lsmRate));
    }

    private static DecoderStats runDecoder(File file, boolean useV2)
    {
        DecoderStats stats = new DecoderStats();

        Listener<IMessage> messageListener = iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                stats.totalMessages++;
                if(message.isValid())
                {
                    stats.validMessages++;
                    if(message.getMessage() != null)
                    {
                        stats.bitErrors += Math.max(message.getMessage().getCorrectedBitCount(), 0);
                    }
                }
            }
            else if(iMessage instanceof SyncLossMessage)
            {
                stats.syncLosses++;
            }
        };

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            if(useV2)
            {
                P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
                decoder.setMessageListener(messageListener);
                decoder.start();

                source.setListener(iNativeBuffer -> {
                    Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });
                source.start();
                decoder.setSampleRate(source.getSampleRate());

                processFile(source);
                decoder.stop();
            }
            else
            {
                P25P1DecoderLSM decoder = new P25P1DecoderLSM();
                decoder.setMessageListener(messageListener);
                decoder.start();

                source.setListener(iNativeBuffer -> {
                    Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                    while(it.hasNext())
                    {
                        decoder.receive(it.next());
                    }
                });
                source.start();
                decoder.setSampleRate(source.getSampleRate());

                processFile(source);
                decoder.stop();
            }
        }
        catch(IOException e)
        {
            System.out.println("ERROR: " + e.getMessage());
        }

        System.out.println("  Valid: " + stats.validMessages + " / Total: " + stats.totalMessages +
                " / Sync Losses: " + stats.syncLosses + " / Bit Errors: " + stats.bitErrors);

        return stats;
    }

    private static void processFile(ComplexWaveSource source) throws IOException
    {
        try
        {
            while(true)
            {
                source.next(2048, true);
            }
        }
        catch(Exception e)
        {
            // End of file
        }
    }

    private static class DecoderStats
    {
        int validMessages = 0;
        int totalMessages = 0;
        int syncLosses = 0;
        int bitErrors = 0;
    }
}
