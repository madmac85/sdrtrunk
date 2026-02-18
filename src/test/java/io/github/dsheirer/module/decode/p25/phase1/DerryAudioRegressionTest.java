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

import io.github.dsheirer.audio.convert.InputAudioFormat;
import io.github.dsheirer.audio.convert.MP3AudioConverter;
import io.github.dsheirer.audio.convert.MP3Setting;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Audio regression test for C4FM dispatch tone investigation (013-fix-c4fm-audio-corruption).
 *
 * Processes baseband WAV segments through the C4FM decoder with JMBE IMBE decoding
 * and produces MP3 audio output. Supports toggling the syncDetected() guard to compare
 * pre-merge (guard off) vs post-merge (guard on) behavior.
 *
 * Usage:
 *   java DerryAudioRegressionTest <baseband-segment.wav> <jmbe-jar-path> <output-dir> [mode]
 *
 * Modes:
 *   fixed  - Run with sync guard disabled (pre-merge behavior)
 *   broken - Run with sync guard enabled (post-merge behavior)
 *   both   - Run both modes and produce comparison (default)
 */
public class DerryAudioRegressionTest
{
    public static void main(String[] args)
    {
        if(args.length < 3)
        {
            System.out.println("Usage: DerryAudioRegressionTest <baseband.wav> <jmbe.jar> <output-dir> [mode] [codec-reset]");
            System.out.println("  mode: fixed (default), broken, both");
            System.out.println("  codec-reset: none (default), percall, perldu");
            return;
        }

        File basebandFile = new File(args[0]);
        Path jmbePath = Paths.get(args[1]);
        Path outputDir = Paths.get(args[2]);
        String mode = args.length > 3 ? args[3] : "both";
        String codecReset = args.length > 4 ? args[4] : "none";

        if(!basebandFile.exists())
        {
            System.out.println("ERROR: Baseband file not found: " + basebandFile);
            return;
        }

        // Load JMBE codec
        TestJmbeCodecLoader codec = new TestJmbeCodecLoader(jmbePath);
        if(!codec.isLoaded())
        {
            System.out.println("ERROR: Failed to load JMBE codec: " + codec.getLoadError());
            System.out.println("  Skipping audio output. LDU metrics will still be reported.");
            codec = null;
        }
        else
        {
            System.out.println("JMBE codec loaded: " + codec.getVersion());
        }

        try
        {
            Files.createDirectories(outputDir);
        }
        catch(IOException e)
        {
            System.out.println("ERROR: Cannot create output directory: " + e.getMessage());
            return;
        }

        if("fixed".equals(mode) || "both".equals(mode))
        {
            System.out.println();
            System.out.println("=== GUARD OFF MODE (pre-merge behavior — accepts all syncs) ===");
            System.out.println("  Codec reset: " + codecReset);
            runDecoder(basebandFile, codec, outputDir.resolve("tone_guard_off.mp3"), false, codecReset);
        }

        if("broken".equals(mode) || "both".equals(mode))
        {
            System.out.println();
            System.out.println("=== GUARD ON MODE (post-merge behavior — blocks syncs during assembly) ===");
            System.out.println("  Codec reset: " + codecReset);
            runDecoder(basebandFile, codec, outputDir.resolve("tone_guard_on.mp3"), true, codecReset);
        }
    }

    private static void runDecoder(File basebandFile, TestJmbeCodecLoader codec, Path mp3Output,
                                   boolean syncGuardEnabled, String codecReset)
    {
        List<float[]> audioBuffers = new ArrayList<>();
        int[] lduCount = {0};
        int[] totalMessages = {0};
        int[] syncLosses = {0};
        int[] syncDetections = {0};
        int[] nidSuccess = {0};
        int[] nidFail = {0};
        P25P1MessageFramer[] framerHolder = {null};
        List<Long> lduTimestamps = new ArrayList<>();
        List<String> imbeFrameDump = new ArrayList<>();
        boolean[] lastLduWasSilence = {true}; // Track silence for per-call reset
        int[] codecResetCount = {0};

        Listener<IMessage> listener = iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                totalMessages[0]++;

                if(message.isValid())
                {
                    P25P1DataUnitID duid = message.getDUID();
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2)
                    {
                        lduCount[0]++;
                        lduTimestamps.add(message.getTimestamp());

                        // Decode IMBE frames to PCM audio and dump frame bytes
                        if(message instanceof LDUMessage ldu)
                        {
                            // Per-LDU codec reset mode
                            if("perldu".equals(codecReset) && codec != null)
                            {
                                codec.reset();
                                codecResetCount[0]++;
                            }

                            List<byte[]> imbeFrames = ldu.getIMBEFrames();
                            StringBuilder sb = new StringBuilder();
                            sb.append("LDU#").append(lduCount[0]).append(" ").append(duid.name())
                              .append(" ts=").append(message.getTimestamp()).append(":");
                            StringBuilder pcmSb = new StringBuilder();
                            pcmSb.append("PCM#").append(lduCount[0]).append(":");
                            for(int i = 0; i < imbeFrames.size(); i++)
                            {
                                byte[] frame = imbeFrames.get(i);
                                sb.append(" F").append(i).append("=");
                                for(byte b : frame)
                                {
                                    sb.append(String.format("%02X", b & 0xFF));
                                }
                                if(codec != null)
                                {
                                    float[] audio = codec.decodeFrame(frame);
                                    if(audio != null && audio.length > 0)
                                    {
                                        // Apply 5.0x NonClippingGain (matches P25P1AudioModule)
                                        float gainFactor = 5.0f;
                                        float maxValue = 0.95f;
                                        for(int s = 0; s < audio.length; s++)
                                        {
                                            float adjusted = audio[s] * gainFactor;
                                            if(adjusted > maxValue) adjusted = maxValue;
                                            if(adjusted < -maxValue) adjusted = -maxValue;
                                            audio[s] = adjusted;
                                        }
                                        audioBuffers.add(audio);
                                        // Compute RMS and peak for this frame
                                        float sumSq = 0, peak = 0;
                                        for(float s : audio)
                                        {
                                            sumSq += s * s;
                                            float abs = Math.abs(s);
                                            if(abs > peak) peak = abs;
                                        }
                                        float rms = (float)Math.sqrt(sumSq / audio.length);
                                        pcmSb.append(String.format(" F%d:rms=%.4f,pk=%.4f", i, rms, peak));
                                    }
                                }
                            }
                            imbeFrameDump.add(sb.toString());
                            imbeFrameDump.add(pcmSb.toString());

                            // Per-call codec reset: detect silence→audio transitions
                            if("percall".equals(codecReset) && codec != null)
                            {
                                // Check if this LDU is silence (first frame RMS < 0.01 after gain)
                                boolean isSilence = true;
                                if(!audioBuffers.isEmpty())
                                {
                                    float[] lastFrame = audioBuffers.get(audioBuffers.size() - 1);
                                    float rmsCheck = 0;
                                    for(float sample : lastFrame)
                                    {
                                        rmsCheck += sample * sample;
                                    }
                                    rmsCheck = (float)Math.sqrt(rmsCheck / lastFrame.length);
                                    isSilence = rmsCheck < 0.01f;
                                }
                                // If previous LDU was silence and this one isn't, reset codec
                                // (simulates SDRTrunk's squelch-close reset at call boundaries)
                                if(lastLduWasSilence[0] && !isSilence)
                                {
                                    // This is a new call starting — but don't reset here,
                                    // the reset should have happened BEFORE the call's LDUs.
                                    // For the NEXT call, we set up the reset.
                                }
                                // If this LDU is silence after a non-silence, do the reset
                                if(isSilence && !lastLduWasSilence[0])
                                {
                                    codec.reset();
                                    codecResetCount[0]++;
                                }
                                lastLduWasSilence[0] = isSilence;
                            }
                        }
                    }
                }
            }
            else if(iMessage instanceof io.github.dsheirer.message.SyncLossMessage)
            {
                syncLosses[0]++;
            }
        };

        try(ComplexWaveSource source = new ComplexWaveSource(basebandFile, false))
        {
            P25P1DecoderC4FM decoder = new P25P1DecoderC4FM();
            decoder.setMessageListener(listener);

            // Configure sync guard via reflection
            try
            {
                Field framerField = decoder.getClass().getDeclaredField("mMessageFramer");
                framerField.setAccessible(true);
                P25P1MessageFramer framer = (P25P1MessageFramer) framerField.get(decoder);
                framer.mSyncGuardEnabled = syncGuardEnabled;
                System.out.println("  Sync guard " + (syncGuardEnabled ? "ENABLED" : "DISABLED"));
            }
            catch(Exception e)
            {
                System.out.println("  WARNING: Could not configure sync guard: " + e.getMessage());
            }

            decoder.start();

            source.setListener(iNativeBuffer -> {
                var it = iNativeBuffer.iterator();
                while(it.hasNext())
                {
                    decoder.receive(it.next());
                }
            });
            source.start();
            decoder.setSampleRate(source.getSampleRate());

            // Process entire file
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

            // Extract framer diagnostics
            try
            {
                Field framerField = decoder.getClass().getDeclaredField("mMessageFramer");
                framerField.setAccessible(true);
                P25P1MessageFramer framer = (P25P1MessageFramer) framerField.get(decoder);
                syncDetections[0] = framer.getSyncDetectionCount();
                nidSuccess[0] = framer.getNIDDecodeSuccessCount();
                nidFail[0] = framer.getNIDDecodeFailCount();
                framerHolder[0] = framer;
            }
            catch(Exception e)
            {
                // Ignore
            }

            decoder.stop();
        }
        catch(IOException e)
        {
            System.out.println("  ERROR: " + e.getMessage());
            return;
        }

        // Report statistics
        System.out.printf("  Total Messages:   %d%n", totalMessages[0]);
        System.out.printf("  LDU Frames:       %d%n", lduCount[0]);
        System.out.printf("  Audio Duration:    %.1fs (LDU x 180ms)%n", lduCount[0] * 0.18);
        System.out.printf("  Sync Losses:       %d%n", syncLosses[0]);
        System.out.printf("  Sync Detections:   %d%n", syncDetections[0]);
        System.out.printf("  NID Success:       %d%n", nidSuccess[0]);
        System.out.printf("  NID Fail:          %d%n", nidFail[0]);
        System.out.printf("  IMBE Audio Frames: %d (%.1fs @ 8kHz)%n",
                audioBuffers.size(), audioBuffers.size() * 160.0 / 8000);

        // Debug: report sync guard and codec reset activity
        if(framerHolder[0] != null)
        {
            System.out.printf("  Sync Blocked:      %d (syncs rejected by guard)%n", framerHolder[0].mSyncBlockedCount);
        }
        System.out.printf("  Codec Resets:      %d%n", codecResetCount[0]);

        // Dump IMBE frames
        System.out.println();
        System.out.println("  --- IMBE Frame Dump ---");
        for(String line : imbeFrameDump)
        {
            System.out.println("  " + line);
        }

        // Write MP3 output
        if(!audioBuffers.isEmpty() && codec != null)
        {
            try
            {
                MP3AudioConverter converter = new MP3AudioConverter(InputAudioFormat.SR_8000,
                        MP3Setting.CBR_16, true);  // normalization ON to match SDRTrunk production
                List<byte[]> mp3Frames = converter.convert(audioBuffers);

                try(FileOutputStream out = new FileOutputStream(mp3Output.toFile()))
                {
                    for(byte[] frame : mp3Frames)
                    {
                        out.write(frame);
                    }
                }

                System.out.printf("  MP3 Output:        %s (%d bytes)%n", mp3Output, mp3Output.toFile().length());
            }
            catch(Exception e)
            {
                System.out.println("  ERROR writing MP3: " + e.getMessage());
            }
        }
        else
        {
            System.out.println("  MP3 Output:        SKIPPED (no audio data or JMBE not available)");
        }

        // LDU gap analysis
        if(lduTimestamps.size() > 1)
        {
            int gapCount = 0;
            long maxGapMs = 0;
            for(int i = 1; i < lduTimestamps.size(); i++)
            {
                long gapMs = lduTimestamps.get(i) - lduTimestamps.get(i - 1);
                if(gapMs > 500)
                {
                    gapCount++;
                    maxGapMs = Math.max(maxGapMs, gapMs);
                }
            }
            System.out.printf("  LDU Gaps (>500ms): %d (max gap: %dms)%n", gapCount, maxGapMs);
        }
    }
}
