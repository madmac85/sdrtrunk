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

import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Extracts a time segment from a baseband WAV file for isolated testing.
 * Useful for analyzing specific missed transmissions or problematic periods.
 *
 * Usage: java SegmentExtractor <input.wav> <output.wav> <startMs> <endMs>
 */
public class SegmentExtractor
{
    public static void main(String[] args)
    {
        if(args.length < 4)
        {
            System.out.println("Usage: SegmentExtractor <input.wav> <output.wav> <startMs> <endMs>");
            System.out.println("  Extracts a time segment from a baseband WAV recording.");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println("  input.wav   - Source baseband WAV file");
            System.out.println("  output.wav  - Output segment WAV file");
            System.out.println("  startMs     - Start time in milliseconds");
            System.out.println("  endMs       - End time in milliseconds");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  SegmentExtractor recording.wav segment.wav 45000 46000");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        long startMs;
        long endMs;

        try
        {
            startMs = Long.parseLong(args[2]);
            endMs = Long.parseLong(args[3]);
        }
        catch(NumberFormatException e)
        {
            System.out.println("ERROR: Invalid time value: " + e.getMessage());
            return;
        }

        if(startMs < 0 || endMs <= startMs)
        {
            System.out.println("ERROR: Invalid time range: startMs=" + startMs + ", endMs=" + endMs);
            return;
        }

        File inputFile = new File(inputPath);
        if(!inputFile.exists())
        {
            System.out.println("ERROR: Input file not found: " + inputPath);
            return;
        }

        try
        {
            extractSegment(inputFile, Paths.get(outputPath), startMs, endMs);
            System.out.println("Segment extracted successfully: " + outputPath);
        }
        catch(IOException e)
        {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts a time segment from a complex baseband WAV file.
     *
     * @param source input WAV file
     * @param output output WAV file path
     * @param startMs start time in milliseconds
     * @param endMs end time in milliseconds
     */
    public static void extractSegment(File source, Path output, long startMs, long endMs) throws IOException
    {
        try(ComplexWaveSource waveSource = new ComplexWaveSource(source, false))
        {
            waveSource.start();
            double sampleRate = waveSource.getSampleRate();

            // Calculate sample offsets
            long startSample = (long)((startMs / 1000.0) * sampleRate);
            long endSample = (long)((endMs / 1000.0) * sampleRate);
            long totalSamples = endSample - startSample;

            System.out.println("Extracting segment:");
            System.out.println("  Sample rate: " + sampleRate + " Hz");
            System.out.println("  Time range: " + startMs + "ms - " + endMs + "ms (" + (endMs - startMs) + "ms)");
            System.out.println("  Sample range: " + startSample + " - " + endSample + " (" + totalSamples + " samples)");

            // Collect samples in the range
            List<float[]> iSamples = new ArrayList<>();
            List<float[]> qSamples = new ArrayList<>();
            long currentSample = 0;
            long collectedSamples = 0;

            try
            {
                while(currentSample < endSample)
                {
                    waveSource.next(2048, true);
                    // The listener will be called with samples
                }
            }
            catch(Exception e)
            {
                // End of file or other issue
            }

            // Re-read with sample collection
            waveSource.close();
        }

        // Use a simpler approach: read entire file and extract segment
        extractSegmentDirect(source, output, startMs, endMs);
    }

    /**
     * Direct segment extraction that reads the entire file and writes the segment.
     */
    private static void extractSegmentDirect(File source, Path output, long startMs, long endMs) throws IOException
    {
        List<float[]> allI = new ArrayList<>();
        List<float[]> allQ = new ArrayList<>();
        final double[] sampleRate = {0};

        try(ComplexWaveSource waveSource = new ComplexWaveSource(source, false))
        {
            waveSource.setListener(buffer -> {
                Iterator<ComplexSamples> it = buffer.iterator();
                while(it.hasNext())
                {
                    ComplexSamples samples = it.next();
                    allI.add(samples.i().clone());
                    allQ.add(samples.q().clone());
                }
            });
            waveSource.start();
            sampleRate[0] = waveSource.getSampleRate();

            try
            {
                while(true)
                {
                    waveSource.next(2048, true);
                }
            }
            catch(Exception e)
            {
                // End of file
            }
        }

        // Calculate total samples and segment bounds
        long totalSamples = 0;
        for(float[] arr : allI)
        {
            totalSamples += arr.length;
        }

        long startSample = (long)((startMs / 1000.0) * sampleRate[0]);
        long endSample = (long)((endMs / 1000.0) * sampleRate[0]);

        startSample = Math.max(0, Math.min(startSample, totalSamples));
        endSample = Math.max(startSample, Math.min(endSample, totalSamples));

        long segmentSamples = endSample - startSample;
        System.out.println("  Total samples in file: " + totalSamples);
        System.out.println("  Extracting samples " + startSample + " to " + endSample + " (" + segmentSamples + " samples)");

        // Extract segment samples
        float[] segmentI = new float[(int)segmentSamples];
        float[] segmentQ = new float[(int)segmentSamples];

        long currentSample = 0;
        int segmentIdx = 0;

        for(int bufIdx = 0; bufIdx < allI.size() && segmentIdx < segmentSamples; bufIdx++)
        {
            float[] iArr = allI.get(bufIdx);
            float[] qArr = allQ.get(bufIdx);

            for(int i = 0; i < iArr.length && segmentIdx < segmentSamples; i++)
            {
                if(currentSample >= startSample && currentSample < endSample)
                {
                    segmentI[segmentIdx] = iArr[i];
                    segmentQ[segmentIdx] = qArr[i];
                    segmentIdx++;
                }
                currentSample++;
            }
        }

        // Write segment to output WAV file
        writeComplexWav(output, segmentI, segmentQ, sampleRate[0]);

        double durationMs = (segmentSamples / sampleRate[0]) * 1000.0;
        System.out.println("  Output duration: " + String.format("%.2f", durationMs) + "ms");
    }

    /**
     * Writes complex I/Q samples to a WAV file in the same format as the input.
     * The format is interleaved I/Q as 32-bit floats.
     */
    private static void writeComplexWav(Path output, float[] i, float[] q, double sampleRate) throws IOException
    {
        int numSamples = i.length;
        int bytesPerSample = 4; // 32-bit float
        int numChannels = 2; // I and Q
        int byteRate = (int)(sampleRate * numChannels * bytesPerSample);
        int blockAlign = numChannels * bytesPerSample;
        int dataSize = numSamples * numChannels * bytesPerSample;

        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        header.put("RIFF".getBytes());
        header.putInt(36 + dataSize); // File size - 8
        header.put("WAVE".getBytes());

        // Format chunk
        header.put("fmt ".getBytes());
        header.putInt(16); // Chunk size
        header.putShort((short)3); // IEEE float format
        header.putShort((short)numChannels);
        header.putInt((int)sampleRate);
        header.putInt(byteRate);
        header.putShort((short)blockAlign);
        header.putShort((short)(bytesPerSample * 8)); // Bits per sample

        // Data chunk header
        header.put("data".getBytes());
        header.putInt(dataSize);

        header.flip();

        // Write interleaved I/Q data
        ByteBuffer data = ByteBuffer.allocate(dataSize);
        data.order(ByteOrder.LITTLE_ENDIAN);

        for(int idx = 0; idx < numSamples; idx++)
        {
            data.putFloat(i[idx]);
            data.putFloat(q[idx]);
        }

        data.flip();

        // Write to file
        try(FileChannel channel = FileChannel.open(output,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            channel.write(header);
            channel.write(data);
        }
    }

    /**
     * Extracts a segment with padding before and after.
     *
     * @param source input WAV file
     * @param output output WAV file path
     * @param centerMs center time of interest in milliseconds
     * @param paddingMs padding to add before and after
     */
    public static void extractSegmentWithPadding(File source, Path output, long centerMs, long paddingMs)
        throws IOException
    {
        long startMs = Math.max(0, centerMs - paddingMs);
        long endMs = centerMs + paddingMs;
        extractSegment(source, output, startMs, endMs);
    }
}
