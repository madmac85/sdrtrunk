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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Extracts individual transmissions from baseband recordings as separate WAV files
 * for focused analysis and testing.
 */
public class TransmissionExtractor
{
    // Buffer to add before/after transmission boundaries
    private static final long DEFAULT_BUFFER_MS = 200;

    /**
     * Extracts a single transmission to a new WAV file.
     *
     * @param sourceFile the source baseband WAV file
     * @param outputDir directory for output files
     * @param transmission the transmission to extract
     * @param bufferMs milliseconds to add before/after boundaries
     * @return the output file path
     */
    public File extractTransmission(File sourceFile, File outputDir, Transmission transmission, long bufferMs)
        throws IOException, UnsupportedAudioFileException
    {
        // Determine output filename
        String baseName = sourceFile.getName().replace(".wav", "");
        String outputName = String.format("%s_tx%03d_%d-%d.wav",
            baseName, transmission.index(), transmission.startMs(), transmission.endMs());
        File outputFile = new File(outputDir, outputName);

        // Calculate sample range with buffer
        long startMs = Math.max(0, transmission.startMs() - bufferMs);
        long endMs = transmission.endMs() + bufferMs;

        extractSegment(sourceFile, outputFile, startMs, endMs);
        return outputFile;
    }

    /**
     * Extracts a time segment from a WAV file to a new file.
     *
     * @param sourceFile input WAV file
     * @param outputFile output WAV file
     * @param startMs start time in milliseconds
     * @param endMs end time in milliseconds
     */
    public void extractSegment(File sourceFile, File outputFile, long startMs, long endMs)
        throws IOException, UnsupportedAudioFileException
    {
        try(AudioInputStream sourceStream = AudioSystem.getAudioInputStream(sourceFile))
        {
            AudioFormat format = sourceStream.getFormat();
            float sampleRate = format.getSampleRate();
            int bytesPerFrame = format.getFrameSize();

            // Calculate byte positions
            long startFrame = (long)(startMs * sampleRate / 1000.0);
            long endFrame = (long)(endMs * sampleRate / 1000.0);
            long frameCount = endFrame - startFrame;

            long startByte = startFrame * bytesPerFrame;
            long byteCount = frameCount * bytesPerFrame;

            // Skip to start position
            sourceStream.skip(startByte);

            // Read the segment
            byte[] data = new byte[(int)byteCount];
            int bytesRead = sourceStream.read(data);

            if(bytesRead <= 0)
            {
                throw new IOException("No data read from source file");
            }

            // Write output WAV file
            writeWavFile(outputFile, format, data, bytesRead);
        }
    }

    /**
     * Extracts worst-performing transmissions from a recording.
     *
     * @param sourceFile the source baseband WAV file
     * @param outputDir directory for output files
     * @param scores list of transmission scores
     * @param count number of worst transmissions to extract
     * @param bufferMs milliseconds to add before/after boundaries
     */
    public void extractWorstTransmissions(File sourceFile, File outputDir,
                                          List<TransmissionScore> scores, int count, long bufferMs)
        throws IOException, UnsupportedAudioFileException
    {
        outputDir.mkdirs();

        // Sort by v2 score ascending (worst first)
        List<TransmissionScore> worst = scores.stream()
            .sorted((a, b) -> Double.compare(a.v2Score(), b.v2Score()))
            .limit(count)
            .toList();

        System.out.println("Extracting " + worst.size() + " worst-performing transmissions:");

        for(TransmissionScore score : worst)
        {
            File outFile = extractTransmission(sourceFile, outputDir, score.transmission(), bufferMs);
            System.out.printf("  TX#%d (%.1f%%): %s%n",
                score.transmission().index(), score.v2Score(), outFile.getName());
        }
    }

    /**
     * Extracts transmissions where v2 regressed compared to LSM.
     *
     * @param sourceFile the source baseband WAV file
     * @param outputDir directory for output files
     * @param scores list of transmission scores
     * @param bufferMs milliseconds to add before/after boundaries
     */
    public void extractRegressions(File sourceFile, File outputDir,
                                   List<TransmissionScore> scores, long bufferMs)
        throws IOException, UnsupportedAudioFileException
    {
        outputDir.mkdirs();

        List<TransmissionScore> regressions = scores.stream()
            .filter(TransmissionScore::isV2Regression)
            .sorted((a, b) -> Integer.compare(a.delta(), b.delta())) // Most negative first
            .toList();

        if(regressions.isEmpty())
        {
            System.out.println("No regressions to extract.");
            return;
        }

        System.out.println("Extracting " + regressions.size() + " regression transmissions:");

        for(TransmissionScore score : regressions)
        {
            File outFile = extractTransmission(sourceFile, outputDir, score.transmission(), bufferMs);
            System.out.printf("  TX#%d: v2=%.1f%% vs LSM=%.1f%% (%d LDUs worse): %s%n",
                score.transmission().index(),
                score.v2Score(),
                score.lsmScore(),
                -score.delta(),
                outFile.getName());
        }
    }

    /**
     * Writes a WAV file with the given audio data.
     */
    private void writeWavFile(File outputFile, AudioFormat format, byte[] data, int dataLength)
        throws IOException
    {
        try(RandomAccessFile raf = new RandomAccessFile(outputFile, "rw"))
        {
            int channels = format.getChannels();
            int sampleSizeInBits = format.getSampleSizeInBits();
            int sampleRate = (int)format.getSampleRate();
            int byteRate = sampleRate * channels * sampleSizeInBits / 8;
            int blockAlign = channels * sampleSizeInBits / 8;

            // RIFF header
            raf.writeBytes("RIFF");
            writeIntLE(raf, 36 + dataLength); // File size - 8
            raf.writeBytes("WAVE");

            // fmt chunk
            raf.writeBytes("fmt ");
            writeIntLE(raf, 16); // Chunk size
            writeShortLE(raf, (short)1); // PCM format
            writeShortLE(raf, (short)channels);
            writeIntLE(raf, sampleRate);
            writeIntLE(raf, byteRate);
            writeShortLE(raf, (short)blockAlign);
            writeShortLE(raf, (short)sampleSizeInBits);

            // data chunk
            raf.writeBytes("data");
            writeIntLE(raf, dataLength);
            raf.write(data, 0, dataLength);
        }
    }

    private void writeIntLE(RandomAccessFile raf, int value) throws IOException
    {
        raf.writeByte(value & 0xFF);
        raf.writeByte((value >> 8) & 0xFF);
        raf.writeByte((value >> 16) & 0xFF);
        raf.writeByte((value >> 24) & 0xFF);
    }

    private void writeShortLE(RandomAccessFile raf, short value) throws IOException
    {
        raf.writeByte(value & 0xFF);
        raf.writeByte((value >> 8) & 0xFF);
    }

    /**
     * Command-line interface for extracting a time segment.
     */
    public static void main(String[] args)
    {
        if(args.length < 4)
        {
            System.out.println("Usage: TransmissionExtractor <input.wav> <output.wav> <startMs> <endMs>");
            System.out.println("  Extracts a time segment from a baseband WAV file.");
            return;
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        long startMs = Long.parseLong(args[2]);
        long endMs = Long.parseLong(args[3]);

        if(!inputFile.exists())
        {
            System.out.println("ERROR: Input file not found: " + inputFile);
            return;
        }

        try
        {
            TransmissionExtractor extractor = new TransmissionExtractor();
            extractor.extractSegment(inputFile, outputFile, startMs, endMs);
            System.out.println("Extracted segment to: " + outputFile.getAbsolutePath());
            System.out.println("  Time range: " + startMs + " - " + endMs + " ms");
            System.out.println("  Duration: " + (endMs - startMs) + " ms");
        }
        catch(Exception e)
        {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
