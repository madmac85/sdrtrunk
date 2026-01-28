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

import io.github.dsheirer.audio.AudioFormats;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.record.wave.WaveWriter;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Audio comparison test that extends LSMv2ComparisonTest to capture and save decoded audio.
 * Measures actual audio duration and identifies missed transmissions.
 *
 * Usage: java AudioComparisonTest <path-to-baseband.wav> <path-to-jmbe.jar> [nac] [output-dir]
 */
public class AudioComparisonTest
{
    private static int sConfiguredNAC = 0;
    private static String sV2Diagnostics = "";

    public static void main(String[] args)
    {
        if(args.length < 2)
        {
            System.out.println("Usage: AudioComparisonTest <path-to-baseband.wav> <path-to-jmbe.jar> [nac] [output-dir]");
            System.out.println("  Plays the baseband recording through both LSM and LSM v2 decoders,");
            System.out.println("  decodes IMBE audio, and saves to WAV files for comparison.");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println("  baseband.wav  - Input baseband recording");
            System.out.println("  jmbe.jar      - Path to JMBE library JAR");
            System.out.println("  nac           - Optional: Known NAC value (0-4095)");
            System.out.println("  output-dir    - Optional: Directory for output files (default: current dir)");
            return;
        }

        String basebandPath = args[0];
        String jmbePath = args[1];
        String outputDir = ".";

        File basebandFile = new File(basebandPath);
        if(!basebandFile.exists())
        {
            System.out.println("ERROR: Baseband file not found: " + basebandPath);
            return;
        }

        Path jmbeFile = Paths.get(jmbePath);
        if(!Files.exists(jmbeFile))
        {
            System.out.println("ERROR: JMBE library not found: " + jmbePath);
            return;
        }

        // Parse optional arguments
        if(args.length >= 3)
        {
            try
            {
                sConfiguredNAC = Integer.parseInt(args[2]);
                if(sConfiguredNAC < 0 || sConfiguredNAC > 4095)
                {
                    System.out.println("WARNING: NAC must be 0-4095, ignoring: " + sConfiguredNAC);
                    sConfiguredNAC = 0;
                }
            }
            catch(NumberFormatException e)
            {
                System.out.println("WARNING: Invalid NAC value, ignoring: " + args[2]);
            }
        }

        if(args.length >= 4)
        {
            outputDir = args[3];
            File outDirFile = new File(outputDir);
            if(!outDirFile.exists())
            {
                outDirFile.mkdirs();
            }
        }

        // Load JMBE codec
        TestJmbeCodecLoader codec = new TestJmbeCodecLoader(jmbeFile);
        if(!codec.isLoaded())
        {
            System.out.println("ERROR: Failed to load JMBE library: " + codec.getLoadError());
            return;
        }

        System.out.println("=== P25 LSM vs LSM v2 Audio Comparison ===");
        System.out.println("File: " + basebandFile.getName() + " | NAC: " + (sConfiguredNAC > 0 ? sConfiguredNAC : "auto") +
                          " | JMBE: " + codec.getVersion());
        System.out.println();

        // Run LSM (original)
        System.out.println("--- Running LSM (original) ---");
        codec.reset();
        AudioDecoderStats lsmStats = runDecoder(basebandFile, false, codec);

        // Run LSM v2
        System.out.println();
        System.out.println("--- Running LSM v2 (cold-start improvements) ---");
        codec.reset();
        AudioDecoderStats v2Stats = runDecoder(basebandFile, true, codec);

        // Save audio to WAV files
        String baseName = basebandFile.getName().replaceFirst("\\.wav$", "");
        Path lsmAudioPath = Paths.get(outputDir, baseName + "_lsm_audio.wav");
        Path v2AudioPath = Paths.get(outputDir, baseName + "_v2_audio.wav");

        try
        {
            saveAudioToWav(lsmStats, lsmAudioPath);
            saveAudioToWav(v2Stats, v2AudioPath);
        }
        catch(IOException e)
        {
            System.out.println("ERROR: Failed to save audio: " + e.getMessage());
        }

        // Print comparison
        System.out.println();
        System.out.println("=== COMPARISON RESULTS ===");
        System.out.println(String.format("%-25s %10s %10s %10s", "", "LSM", "LSM v2", "Delta"));
        System.out.println(String.format("%-25s %10d %10d %+10d", "LDU Count",
            lsmStats.lduCount, v2Stats.lduCount, v2Stats.lduCount - lsmStats.lduCount));

        double lsmDuration = lsmStats.totalAudioSamples / 8000.0;
        double v2Duration = v2Stats.totalAudioSamples / 8000.0;
        System.out.println(String.format("%-25s %9.2fs %9.2fs %+9.2fs", "Audio Duration",
            lsmDuration, v2Duration, v2Duration - lsmDuration));

        double lsmImbeRate = (lsmStats.imbeDecodeSuccesses + lsmStats.imbeDecodeFailures) > 0 ?
            (double)lsmStats.imbeDecodeSuccesses / (lsmStats.imbeDecodeSuccesses + lsmStats.imbeDecodeFailures) * 100.0 : 0;
        double v2ImbeRate = (v2Stats.imbeDecodeSuccesses + v2Stats.imbeDecodeFailures) > 0 ?
            (double)v2Stats.imbeDecodeSuccesses / (v2Stats.imbeDecodeSuccesses + v2Stats.imbeDecodeFailures) * 100.0 : 0;
        System.out.println(String.format("%-25s %9.1f%% %9.1f%% %+9.1f%%", "IMBE Decode Rate",
            lsmImbeRate, v2ImbeRate, v2ImbeRate - lsmImbeRate));

        System.out.println(String.format("%-25s %10d %10d %+10d", "Bit Errors",
            lsmStats.bitErrors, v2Stats.bitErrors, v2Stats.bitErrors - lsmStats.bitErrors));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Valid Messages",
            lsmStats.validMessages, v2Stats.validMessages, v2Stats.validMessages - lsmStats.validMessages));
        System.out.println(String.format("%-25s %10d %10d %+10d", "Sync Losses",
            lsmStats.syncLosses, v2Stats.syncLosses, v2Stats.syncLosses - lsmStats.syncLosses));

        // Analyze missed transmissions
        System.out.println();
        System.out.println("=== MISSED TRANSMISSIONS (v2) ===");
        MissedTransmissionAnalyzer analyzer = new MissedTransmissionAnalyzer();

        if(v2Stats.energyProfile != null && !v2Stats.lduTimestamps.isEmpty())
        {
            long baseTimestamp = v2Stats.allMessageTimestamps.isEmpty() ? 0 : v2Stats.allMessageTimestamps.get(0);
            List<MissedTransmissionAnalyzer.MissedTransmission> v2Missed =
                analyzer.analyze(v2Stats.energyProfile, v2Stats.lduTimestamps, baseTimestamp);

            if(v2Missed.isEmpty())
            {
                System.out.println("No significant missed transmissions detected.");
            }
            else
            {
                System.out.println("High-energy periods with no decoded audio:");
                int count = 0;
                for(MissedTransmissionAnalyzer.MissedTransmission missed : v2Missed)
                {
                    count++;
                    System.out.printf("  %d. %dms - %dms (%dms) peak=%.2f%n",
                        count, missed.startMs(), missed.endMs(), missed.durationMs(), missed.peakEnergy());
                    if(count >= 10) break; // Limit output
                }
                if(v2Missed.size() > 10)
                {
                    System.out.println("  ... and " + (v2Missed.size() - 10) + " more");
                }
            }
        }
        else
        {
            System.out.println("Energy profile not available for analysis.");
        }

        System.out.println();
        System.out.println("=== LSM v2 DIAGNOSTICS ===");
        System.out.println(sV2Diagnostics);

        System.out.println();
        System.out.println("=== OUTPUT FILES ===");
        System.out.println("  LSM audio:    " + lsmAudioPath + " (" + String.format("%.2fs", lsmDuration) + ")");
        System.out.println("  v2 audio:     " + v2AudioPath + " (" + String.format("%.2fs", v2Duration) + ")");
    }

    /**
     * Runs a decoder and collects audio statistics.
     */
    private static AudioDecoderStats runDecoder(File file, boolean useV2, TestJmbeCodecLoader codec)
    {
        AudioDecoderStats stats = new AudioDecoderStats();

        Listener<IMessage> messageListener = iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                stats.totalMessages++;
                stats.allMessageTimestamps.add(message.getTimestamp());

                if(message.isValid())
                {
                    stats.validMessages++;
                    if(message.getMessage() != null)
                    {
                        stats.bitErrors += Math.max(message.getMessage().getCorrectedBitCount(), 0);
                    }

                    P25P1DataUnitID duid = message.getDUID();
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2)
                    {
                        stats.lduCount++;
                        stats.lduTimestamps.add(message.getTimestamp());

                        // Decode IMBE audio
                        if(message instanceof LDUMessage ldu)
                        {
                            List<byte[]> imbeFrames = ldu.getIMBEFrames();
                            for(byte[] frame : imbeFrames)
                            {
                                float[] audio = codec.decodeFrame(frame);
                                if(audio != null && audio.length > 0)
                                {
                                    stats.audioBuffers.add(audio);
                                    stats.totalAudioSamples += audio.length;
                                    stats.imbeDecodeSuccesses++;
                                }
                                else
                                {
                                    stats.imbeDecodeFailures++;
                                }
                            }
                        }
                    }
                }
            }
            else if(iMessage instanceof SyncLossMessage syncLoss)
            {
                stats.syncLosses++;
                stats.syncLossTimestamps.add(syncLoss.getTimestamp());
            }
        };

        try(ComplexWaveSource source = new ComplexWaveSource(file, false))
        {
            // Create energy profile for missed transmission analysis
            MissedTransmissionAnalyzer.EnergyProfile energyProfile =
                new MissedTransmissionAnalyzer.EnergyProfile(source.getSampleRate());
            stats.energyProfile = energyProfile;

            if(useV2)
            {
                P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
                decoder.setMessageListener(messageListener);

                if(sConfiguredNAC > 0)
                {
                    decoder.setConfiguredNAC(sConfiguredNAC);
                }

                decoder.start();

                source.setListener(iNativeBuffer -> {
                    Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                    while(it.hasNext())
                    {
                        ComplexSamples samples = it.next();
                        decoder.receive(samples);
                        // Also track energy for missed transmission analysis
                        energyProfile.addSamples(samples.i(), samples.q());
                    }
                });
                source.start();
                decoder.setSampleRate(source.getSampleRate());

                processFile(source);
                sV2Diagnostics = decoder.getDiagnostics();
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
                        ComplexSamples samples = it.next();
                        decoder.receive(samples);
                        energyProfile.addSamples(samples.i(), samples.q());
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

        System.out.println("  LDUs: " + stats.lduCount + " | IMBE frames: " + stats.imbeDecodeSuccesses +
                          " | Audio samples: " + stats.totalAudioSamples);

        return stats;
    }

    /**
     * Processes the entire wave file through the decoder.
     */
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

    /**
     * Saves decoded audio buffers to a WAV file.
     */
    private static void saveAudioToWav(AudioDecoderStats stats, Path outputPath) throws IOException
    {
        if(stats.audioBuffers.isEmpty())
        {
            System.out.println("  No audio to save for: " + outputPath.getFileName());
            return;
        }

        // Create temporary file first, then rename
        Path tempPath = Paths.get(outputPath.toString() + ".tmp");

        try(WaveWriter writer = new WaveWriter(AudioFormats.PCM_SIGNED_8000_HZ_16_BIT_MONO, tempPath))
        {
            for(float[] buffer : stats.audioBuffers)
            {
                ByteBuffer pcm = convertFloatToPcm16(buffer);
                writer.writeData(pcm);
            }
        }

        // Rename temp file to final output
        Files.move(tempPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Converts float audio samples to 16-bit PCM.
     * Input is assumed to be in the range [-1.0, 1.0].
     */
    private static ByteBuffer convertFloatToPcm16(float[] samples)
    {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2);
        buffer.order(ByteOrder.BIG_ENDIAN); // AudioFormats uses big-endian

        for(float sample : samples)
        {
            // Clamp to [-1.0, 1.0] and convert to 16-bit signed
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            short pcm = (short)(clamped * 32767);
            buffer.putShort(pcm);
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Extended decoder statistics with audio capture.
     */
    private static class AudioDecoderStats
    {
        int validMessages = 0;
        int totalMessages = 0;
        int syncLosses = 0;
        int bitErrors = 0;
        int lduCount = 0;

        // Audio capture
        List<float[]> audioBuffers = new ArrayList<>();
        long totalAudioSamples = 0;
        int imbeDecodeSuccesses = 0;
        int imbeDecodeFailures = 0;

        // Timestamp tracking
        List<Long> lduTimestamps = new ArrayList<>();
        List<Long> syncLossTimestamps = new ArrayList<>();
        List<Long> allMessageTimestamps = new ArrayList<>();

        // Energy profile for missed transmission analysis
        MissedTransmissionAnalyzer.EnergyProfile energyProfile;
    }
}
