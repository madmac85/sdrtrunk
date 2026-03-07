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
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Unified decode quality test. Reads a playlist XML to determine the correct decoder
 * per baseband file, runs the decode, and outputs metrics as JSON.
 *
 * Usage:
 *   java DecodeQualityTest --samples dir --playlist xml --output dir [--jmbe jar] [--mode quick|full]
 */
public class DecodeQualityTest
{
    record ChannelConfig(String name, String system, String site, long frequency, String modulation, int nac, String preferredTuner) {}
    record DecodeResult(int lduCount, int validMessages, int totalMessages, int syncBlockedCount, int bitErrors, int syncLosses, double signalSeconds, double totalFileSeconds) {}
    record AudioResult(int segmentCount, double totalSeconds) {}

    /** Wraps any P25P1 decoder to provide a uniform interface. */
    interface DecoderWrapper
    {
        void setMessageListener(Listener<IMessage> listener);
        void start();
        void stop();
        void setSampleRate(double rate);
        void receive(ComplexSamples samples);
        int getSyncBlockedCount();
    }

    public static void main(String[] args)
    {
        String samplesDir = null, playlistPath = null, outputDir = null, jmbePath = null, mode = "quick";
        String forceMod = null;
        int forceNac = -1;

        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--samples" -> samplesDir = args[++i];
                case "--playlist" -> playlistPath = args[++i];
                case "--output" -> outputDir = args[++i];
                case "--jmbe" -> jmbePath = args[++i];
                case "--mode" -> mode = args[++i];
                case "--force-mod" -> forceMod = args[++i];
                case "--force-nac" -> forceNac = Integer.parseInt(args[++i]);
            }
        }

        if(samplesDir == null || playlistPath == null || outputDir == null)
        {
            System.out.println("Usage: DecodeQualityTest --samples <dir> --playlist <xml> --output <dir> [--jmbe <jar>] [--mode quick|full]");
            System.exit(1);
            return;
        }

        boolean fullMode = "full".equals(mode);

        TestJmbeCodecLoader codec = null;
        if(fullMode && jmbePath != null)
        {
            codec = new TestJmbeCodecLoader(Paths.get(jmbePath));
            if(!codec.isLoaded()) { System.err.println("WARNING: JMBE load failed: " + codec.getLoadError()); codec = null; }
        }

        List<ChannelConfig> channels = parsePlaylist(new File(playlistPath));
        if(channels.isEmpty()) { System.err.println("ERROR: No channels in playlist"); System.exit(1); return; }
        System.out.printf("Loaded %d channels from playlist%n", channels.size());

        List<File> basebandFiles = findBasebandFiles(new File(samplesDir));
        if(basebandFiles.isEmpty()) { System.err.println("ERROR: No baseband files found"); System.exit(1); return; }
        System.out.printf("Found %d baseband files%n", basebandFiles.size());

        Path outPath = Paths.get(outputDir);
        try { Files.createDirectories(outPath); } catch(IOException e) { System.exit(1); return; }

        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;

        for(File bbFile : basebandFiles)
        {
            long freq = extractFrequency(bbFile.getName());
            ChannelConfig config = findChannel(channels, freq);

            String channelName = config != null ? config.name() : "Unknown";
            String modulation = forceMod != null ? forceMod : (config != null ? config.modulation() : "C4FM");
            int nac = forceNac >= 0 ? forceNac : (config != null ? config.nac() : 0);
            String tuner = config != null ? config.preferredTuner() : "N/A";
            String system = config != null ? config.system() : "";
            String site = config != null ? config.site() : "";
            boolean isFD = channelName.toLowerCase().contains("fire") || channelName.toLowerCase().contains(" fd");

            System.out.printf("%n━━━ %s ━━━%n", bbFile.getName());
            System.out.printf("  Channel: %s | Mod: %s | NAC: %d | Tuner: %s%n", channelName, modulation, nac, tuner);

            DecodeResult result = runDecode(bbFile, modulation, nac);

            int audioSegments = 0;
            double audioSeconds = 0;
            if(fullMode && codec != null)
            {
                Path audioDir = outPath.resolve(sanitize(bbFile.getName()));
                try { Files.createDirectories(audioDir); } catch(IOException e) { /* ignore */ }
                AudioResult ar = decodeAudio(bbFile, modulation, nac, codec, audioDir);
                audioSegments = ar.segmentCount;
                audioSeconds = ar.totalSeconds;
                System.out.printf("  Audio: %.1fs in %d segments (avg %.1fs)%n",
                    audioSeconds, audioSegments, audioSegments > 0 ? audioSeconds / audioSegments : 0);
            }

            double decodeSeconds = result.lduCount * 0.18; // ~180ms per LDU
            double decodeRatio = result.signalSeconds > 0 ? (decodeSeconds / result.signalSeconds) * 100 : 0;
            System.out.printf("  LDUs: %d | Valid: %d/%d | Sync Blocked: %d%n",
                result.lduCount, result.validMessages, result.totalMessages, result.syncBlockedCount);
            System.out.printf("  Signal: %.1fs / %.1fs file | Decode ratio: %.1f%%%n",
                result.signalSeconds, result.totalFileSeconds, decodeRatio);

            if(!first) json.append(",\n");
            first = false;
            json.append(String.format(
                "  {\"file\": \"%s\", \"channel\": \"%s\", \"system\": \"%s\", \"site\": \"%s\", " +
                "\"modulation\": \"%s\", \"nac\": %d, \"tuner\": \"%s\", \"is_fd\": %s, " +
                "\"ldu_count\": %d, \"valid_messages\": %d, \"total_messages\": %d, \"sync_blocked\": %d, " +
                "\"bit_errors\": %d, \"sync_losses\": %d, \"audio_seconds\": %.2f, \"audio_segments\": %d, " +
                "\"signal_seconds\": %.2f, \"total_file_seconds\": %.2f, \"decode_ratio\": %.2f}",
                escapeJson(bbFile.getName()), escapeJson(channelName), escapeJson(system), escapeJson(site),
                modulation, nac, escapeJson(tuner), isFD,
                result.lduCount, result.validMessages, result.totalMessages, result.syncBlockedCount,
                result.bitErrors, result.syncLosses, audioSeconds, audioSegments,
                result.signalSeconds, result.totalFileSeconds, decodeRatio));
        }

        json.append("\n]\n");

        Path metricsFile = outPath.resolve("metrics.json");
        try(FileWriter fw = new FileWriter(metricsFile.toFile()))
        {
            fw.write(json.toString());
            System.out.printf("%n✓ Metrics written to: %s%n", metricsFile);
        }
        catch(IOException e) { System.err.println("ERROR writing metrics: " + e.getMessage()); }
    }

    private static DecoderWrapper createDecoder(String modulation, int nac)
    {
        return switch(modulation.toUpperCase())
        {
            case "CQPSK" ->
            {
                P25P1DecoderLSM d = new P25P1DecoderLSM();
                yield new DecoderWrapper()
                {
                    public void setMessageListener(Listener<IMessage> l) { d.setMessageListener(l); }
                    public void start() { d.start(); }
                    public void stop() { d.stop(); }
                    public void setSampleRate(double r) { d.setSampleRate(r); }
                    public void receive(ComplexSamples s) { d.receive(s); }
                    public int getSyncBlockedCount() { return extractSyncBlockedReflection(d); }
                };
            }
            case "CQPSK_V2" ->
            {
                P25P1DecoderLSMv2 d = new P25P1DecoderLSMv2();
                if(nac > 0) d.setConfiguredNAC(nac);
                yield new DecoderWrapper()
                {
                    public void setMessageListener(Listener<IMessage> l) { d.setMessageListener(l); }
                    public void start() { d.start(); }
                    public void stop() { d.stop(); }
                    public void setSampleRate(double r) { d.setSampleRate(r); }
                    public void receive(ComplexSamples s) { d.receive(s); }
                    public int getSyncBlockedCount() { return d.getMessageFramer().getSyncBlockedCount(); }
                };
            }
            default ->
            {
                P25P1DecoderC4FM d = new P25P1DecoderC4FM();
                yield new DecoderWrapper()
                {
                    public void setMessageListener(Listener<IMessage> l) { d.setMessageListener(l); }
                    public void start() { d.start(); }
                    public void stop() { d.stop(); }
                    public void setSampleRate(double r) { d.setSampleRate(r); }
                    public void receive(ComplexSamples s) { d.receive(s); }
                    public int getSyncBlockedCount() { return extractSyncBlockedReflection(d); }
                };
            }
        };
    }

    private static DecodeResult runDecode(File file, String modulation, int nac)
    {
        int[] ldu = {0}, valid = {0}, total = {0}, bitErr = {0}, syncLoss = {0};
        double[] signalSeconds = {0}, totalFileSeconds = {0};
        Map<String, int[]> duidCounts = new HashMap<>();
        Map<String, int[]> nacCounts = new HashMap<>();

        Listener<IMessage> listener = msg -> {
            if(msg instanceof SyncLossMessage) { syncLoss[0]++; return; }
            if(msg instanceof P25P1Message p)
            {
                total[0]++;
                var duid = p.getDUID();
                duidCounts.computeIfAbsent(duid.name(), k -> new int[]{0, 0});
                duidCounts.get(duid.name())[0]++;
                if(p.isValid())
                {
                    valid[0]++;
                    duidCounts.get(duid.name())[1]++;
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2) ldu[0]++;
                }
                String nacStr = String.valueOf(p.getNAC());
                nacCounts.computeIfAbsent(nacStr, k -> new int[]{0})[0]++;
                if(p.getMessage() != null) bitErr[0] += p.getMessage().getCorrectedBitCount();
            }
        };

        int syncBlocked = 0;
        try(TestComplexWaveSource source = new TestComplexWaveSource(file))
        {
            double sampleRate = source.getSampleRate();
            long[] totalSamples = {0};
            List<Float> bufferRmsValues = new ArrayList<>();
            List<Integer> bufferSizes = new ArrayList<>();

            DecoderWrapper decoder = createDecoder(modulation, nac);
            decoder.setMessageListener(listener);
            decoder.start();
            source.setListener(buf -> {
                var it = buf.iterator();
                while(it.hasNext())
                {
                    ComplexSamples cs = it.next();
                    decoder.receive(cs);

                    float[] iSamples = cs.i();
                    float[] qSamples = cs.q();
                    int len = Math.min(iSamples.length, qSamples.length);
                    totalSamples[0] += len;

                    float sumSq = 0;
                    for(int s = 0; s < len; s++)
                    {
                        sumSq += iSamples[s] * iSamples[s] + qSamples[s] * qSamples[s];
                    }
                    float rms = (float)Math.sqrt(sumSq / len);
                    bufferRmsValues.add(rms);
                    bufferSizes.add(len);
                }
            });
            decoder.setSampleRate(sampleRate);
            while(source.next(2048)) {}
            syncBlocked = decoder.getSyncBlockedCount();
            decoder.stop();

            totalFileSeconds[0] = totalSamples[0] / sampleRate;

            // Adaptive signal detection: compute threshold from distribution of RMS values
            signalSeconds[0] = computeSignalSeconds(bufferRmsValues, bufferSizes, sampleRate);
        }
        catch(Exception e) { System.err.println("  ERROR: " + e.getMessage()); }

        // Print DUID breakdown
        if(!duidCounts.isEmpty())
        {
            System.out.print("  DUIDs: ");
            duidCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> System.out.printf("%s=%d(%dv) ", e.getKey(), e.getValue()[0], e.getValue()[1]));
            System.out.println();
        }
        if(!nacCounts.isEmpty())
        {
            System.out.print("  NACs observed: ");
            nacCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> System.out.printf("%s(×%d) ", e.getKey(), e.getValue()[0]));
            System.out.println();
        }

        return new DecodeResult(ldu[0], valid[0], total[0], syncBlocked, bitErr[0], syncLoss[0], signalSeconds[0], totalFileSeconds[0]);
    }

    /**
     * Computes signal-present seconds using adaptive thresholding on per-buffer RMS values.
     * Uses percentile-based separation: estimates noise floor (10th pct) and signal level (90th pct),
     * then places threshold at geometric mean. Falls back to max*0.3 for unimodal distributions.
     */
    private static double computeSignalSeconds(List<Float> rmsValues, List<Integer> sizes, double sampleRate)
    {
        if(rmsValues.isEmpty()) return 0;

        List<Float> sorted = new ArrayList<>(rmsValues);
        Collections.sort(sorted);
        int n = sorted.size();

        float p10 = sorted.get(n / 10);
        float p90 = sorted.get(Math.min(9 * n / 10, n - 1));
        float maxRms = sorted.get(n - 1);

        float threshold;
        if(maxRms < 0.0001f)
        {
            // Essentially no energy at all
            return 0;
        }
        else if(p90 < p10 * 2.0f)
        {
            // Unimodal distribution — can't reliably separate signal from noise
            // Use 30% of max as threshold (generous for all-signal files)
            threshold = maxRms * 0.3f;
        }
        else
        {
            // Bimodal — geometric mean of noise and signal estimates
            threshold = (float)Math.sqrt(p10 * p90);
            // Ensure threshold is at least slightly above noise
            threshold = Math.max(threshold, p10 * 1.5f);
        }

        long signalSamples = 0;
        for(int i = 0; i < rmsValues.size(); i++)
        {
            if(rmsValues.get(i) > threshold) signalSamples += sizes.get(i);
        }

        return signalSamples / sampleRate;
    }

    private static AudioResult decodeAudio(File file, String modulation, int nac, TestJmbeCodecLoader codec, Path audioDir)
    {
        List<float[]> audioBuffers = new ArrayList<>();
        List<Long> lduTimestamps = new ArrayList<>();

        Listener<IMessage> listener = msg -> {
            if(msg instanceof LDUMessage ldu && ((P25P1Message)msg).isValid())
            {
                lduTimestamps.add(((P25P1Message)msg).getTimestamp());
                for(byte[] frame : ldu.getIMBEFrames())
                {
                    float[] audio = codec.decodeFrame(frame);
                    if(audio != null && audio.length > 0)
                    {
                        for(int s = 0; s < audio.length; s++)
                        {
                            float a = audio[s] * 5.0f;
                            audio[s] = Math.max(-0.95f, Math.min(0.95f, a));
                        }
                        audioBuffers.add(audio);
                    }
                }
            }
        };

        try(TestComplexWaveSource source = new TestComplexWaveSource(file))
        {
            DecoderWrapper decoder = createDecoder(modulation, nac);
            decoder.setMessageListener(listener);
            decoder.start();
            source.setListener(buf -> { var it = buf.iterator(); while(it.hasNext()) decoder.receive(it.next()); });
            decoder.setSampleRate(source.getSampleRate());
            while(source.next(2048)) {}
            decoder.stop();
        }
        catch(Exception e) { System.err.println("  AUDIO ERROR: " + e.getMessage()); }

        if(audioBuffers.isEmpty()) return new AudioResult(0, 0);

        // Segment by LDU gaps >500ms
        List<List<float[]>> segments = new ArrayList<>();
        List<float[]> current = new ArrayList<>();
        int framesPerLdu = 9;
        int bufIdx = 0;

        for(int i = 0; i < lduTimestamps.size(); i++)
        {
            if(i > 0 && (lduTimestamps.get(i) - lduTimestamps.get(i - 1)) > 500)
            {
                if(!current.isEmpty()) { segments.add(current); current = new ArrayList<>(); }
            }
            for(int f = 0; f < framesPerLdu && bufIdx < audioBuffers.size(); f++, bufIdx++)
            {
                current.add(audioBuffers.get(bufIdx));
            }
        }
        if(!current.isEmpty()) segments.add(current);

        double totalSeconds = 0;
        for(int s = 0; s < segments.size(); s++)
        {
            double segSeconds = segments.get(s).size() * 160.0 / 8000.0;
            totalSeconds += segSeconds;
            writeMP3(segments.get(s), audioDir.resolve(String.format("segment_%03d.mp3", s + 1)));
        }

        return new AudioResult(segments.size(), totalSeconds);
    }

    private static void writeMP3(List<float[]> audioBuffers, Path mp3File)
    {
        try(FileOutputStream fos = new FileOutputStream(mp3File.toFile()))
        {
            MP3AudioConverter converter = new MP3AudioConverter(InputAudioFormat.SR_8000, MP3Setting.CBR_16, true);
            List<byte[]> mp3Chunks = converter.convert(audioBuffers);
            for(byte[] chunk : mp3Chunks)
            {
                if(chunk != null && chunk.length > 0) fos.write(chunk);
            }
            List<byte[]> flushChunks = converter.flush();
            for(byte[] chunk : flushChunks)
            {
                if(chunk != null && chunk.length > 0) fos.write(chunk);
            }
        }
        catch(IOException e) { System.err.println("  MP3 write error: " + e.getMessage()); }
    }

    private static int extractSyncBlockedReflection(Object decoder)
    {
        try
        {
            Class<?> clazz = decoder.getClass();
            while(clazz != null)
            {
                try
                {
                    Field f = clazz.getDeclaredField("mMessageFramer");
                    f.setAccessible(true);
                    Object framer = f.get(decoder);
                    if(framer instanceof P25P1MessageFramer mf) return mf.getSyncBlockedCount();
                }
                catch(NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            }
        }
        catch(Exception e) { /* ignore */ }
        return 0;
    }

    static List<ChannelConfig> parsePlaylist(File xmlFile)
    {
        List<ChannelConfig> configs = new ArrayList<>();
        try
        {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            NodeList channelNodes = doc.getElementsByTagName("channel");

            for(int i = 0; i < channelNodes.getLength(); i++)
            {
                Element ch = (Element)channelNodes.item(i);
                String name = ch.getAttribute("name");
                String system = ch.getAttribute("system");
                String site = ch.getAttribute("site");

                NodeList decodeConfigs = ch.getElementsByTagName("decode_configuration");
                if(decodeConfigs.getLength() == 0) continue;
                Element dc = (Element)decodeConfigs.item(0);
                if(!"decodeConfigP25Phase1".equals(dc.getAttribute("type"))) continue;

                String modulation = dc.getAttribute("modulation");
                int nac = 0;
                try { nac = Integer.parseInt(dc.getAttribute("configuredNAC")); } catch(NumberFormatException e) {}

                NodeList sourceConfigs = ch.getElementsByTagName("source_configuration");
                long frequency = 0;
                String preferredTuner = "N/A";
                if(sourceConfigs.getLength() > 0)
                {
                    Element sc = (Element)sourceConfigs.item(0);
                    try { frequency = Long.parseLong(sc.getAttribute("frequency")); } catch(NumberFormatException e) {}
                    String t = sc.getAttribute("preferred_tuner");
                    if(t != null && !t.isEmpty()) preferredTuner = t;
                }

                if(frequency > 0 && modulation != null && !modulation.isEmpty())
                {
                    configs.add(new ChannelConfig(name, system, site, frequency, modulation, nac, preferredTuner));
                }
            }
        }
        catch(Exception e) { System.err.println("ERROR parsing playlist: " + e.getMessage()); }
        return configs;
    }

    static long extractFrequency(String filename)
    {
        String[] parts = filename.split("_");
        if(parts.length >= 3)
        {
            try { return Long.parseLong(parts[2]); } catch(NumberFormatException e) { /* ignore */ }
        }
        return 0;
    }

    static ChannelConfig findChannel(List<ChannelConfig> channels, long frequency)
    {
        for(ChannelConfig ch : channels) { if(ch.frequency() == frequency) return ch; }
        return null;
    }

    static List<File> findBasebandFiles(File dir)
    {
        List<File> files = new ArrayList<>();
        if(dir.isFile() && dir.getName().endsWith("_baseband.wav")) { files.add(dir); return files; }
        if(dir.isDirectory())
        {
            File[] children = dir.listFiles();
            if(children != null)
            {
                Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
                for(File child : children)
                {
                    if(child.isDirectory()) files.addAll(findBasebandFiles(child));
                    else if(child.getName().endsWith("_baseband.wav")) files.add(child);
                }
            }
        }
        return files;
    }

    private static String sanitize(String name)
    {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("_baseband\\.wav$", "");
    }

    private static String escapeJson(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
