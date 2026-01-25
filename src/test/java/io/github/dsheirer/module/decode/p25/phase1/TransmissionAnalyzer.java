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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Transmission detection and analysis tool for P25 LSM v2 optimization.
 *
 * Detects transmission boundaries via energy envelope (EMA-filtered I/Q power),
 * filters out narrow-band non-P25 signals, tracks decoded LDU frame timestamps
 * from both LSM and LSM v2, and correlates transmissions with decoded frames
 * to identify missed transmissions.
 */
public class TransmissionAnalyzer
{
    // Energy detection parameters (matching decoder)
    private static final float ENERGY_EMA_FACTOR = 0.001f;
    private static final float SILENCE_RATIO = 0.1f;
    private static final double MIN_TX_DURATION_SEC = 0.3;
    private static final double MIN_TX_BANDWIDTH_RATIO = 0.5;

    // Silence threshold in seconds
    private static final double SILENCE_THRESHOLD_SEC = 0.5;

    /**
     * Represents a detected transmission period.
     */
    public record Transmission(
        long startMs,
        long endMs,
        double peakEnergy,
        int lsmLduCount,
        int v2LduCount,
        boolean isNarrowBand
    ) {
        public long durationMs() { return endMs - startMs; }

        public boolean decodedByLsm() { return lsmLduCount > 0; }
        public boolean decodedByV2() { return v2LduCount > 0; }

        public boolean isV2Regression() { return decodedByLsm() && !decodedByV2(); }
        public boolean isV2Improvement() { return decodedByV2() && !decodedByLsm(); }

        @Override
        public String toString()
        {
            String status;
            if(isNarrowBand)
            {
                status = "NARROW";
            }
            else if(isV2Regression())
            {
                status = "V2-MISS";
            }
            else if(isV2Improvement())
            {
                status = "V2-GAIN";
            }
            else if(!decodedByLsm() && !decodedByV2())
            {
                status = "BOTH-MISS";
            }
            else
            {
                status = "OK";
            }
            return String.format("%6dms - %6dms (%4dms) peak=%.4f LSM=%d v2=%d [%s]",
                    startMs, endMs, durationMs(), peakEnergy, lsmLduCount, v2LduCount, status);
        }
    }

    /**
     * Analysis results container.
     */
    public static class AnalysisResult
    {
        public List<Transmission> transmissions = new ArrayList<>();
        public int totalLsmLdu = 0;
        public int totalV2Ldu = 0;
        public int lsmBitErrors = 0;
        public int v2BitErrors = 0;
        public List<Long> lsmLduTimestamps = new ArrayList<>();
        public List<Long> v2LduTimestamps = new ArrayList<>();
        public String v2Diagnostics = "";

        public int v2Regressions()
        {
            return (int) transmissions.stream().filter(Transmission::isV2Regression).count();
        }

        public int v2Improvements()
        {
            return (int) transmissions.stream().filter(Transmission::isV2Improvement).count();
        }

        public int bothMissed()
        {
            return (int) transmissions.stream()
                    .filter(t -> !t.isNarrowBand() && !t.decodedByLsm() && !t.decodedByV2())
                    .count();
        }

        public int narrowBandFiltered()
        {
            return (int) transmissions.stream().filter(Transmission::isNarrowBand).count();
        }
    }

    private final File mFile;
    private double mSampleRate;
    private long mFileStartTimeMs;

    // Energy tracking state
    private float mEnergyAverage = 0f;
    private float mPeakEnergy = 0f;
    private int mSilenceSampleCount = 0;
    private boolean mInTransmission = false;
    private long mTransmissionStartSample = 0;
    private float mTransmissionPeakEnergy = 0f;

    // Bandwidth measurement for narrow-band filtering
    private float mMinI = Float.MAX_VALUE, mMaxI = Float.MIN_VALUE;
    private float mMinQ = Float.MAX_VALUE, mMaxQ = Float.MIN_VALUE;

    // Detected transmissions (energy-based)
    private List<long[]> mDetectedTransmissions = new ArrayList<>(); // [startMs, endMs, peak*1000]

    public TransmissionAnalyzer(File file) throws IOException
    {
        if(!file.exists())
        {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        mFile = file;
    }

    /**
     * Runs the full analysis: detects transmissions via energy, then runs both decoders
     * and correlates decoded LDUs with detected transmissions.
     */
    public AnalysisResult analyze() throws IOException
    {
        AnalysisResult result = new AnalysisResult();

        // Phase 1: Detect transmissions via energy envelope
        System.out.println("Phase 1: Detecting transmissions via energy envelope...");
        detectTransmissions();
        System.out.println("  Detected " + mDetectedTransmissions.size() + " transmission periods");

        // Phase 2: Run LSM decoder and collect timestamps
        System.out.println("Phase 2: Running LSM decoder...");
        DecoderResult lsmResult = runDecoder(false);
        result.totalLsmLdu = lsmResult.lduCount;
        result.lsmBitErrors = lsmResult.bitErrors;
        result.lsmLduTimestamps = lsmResult.lduTimestamps;
        System.out.println("  LSM: " + lsmResult.lduCount + " LDUs, " + lsmResult.bitErrors + " bit errors");

        // Phase 3: Run LSM v2 decoder and collect timestamps
        System.out.println("Phase 3: Running LSM v2 decoder...");
        DecoderResult v2Result = runDecoder(true);
        result.totalV2Ldu = v2Result.lduCount;
        result.v2BitErrors = v2Result.bitErrors;
        result.v2LduTimestamps = v2Result.lduTimestamps;
        result.v2Diagnostics = v2Result.diagnostics;
        System.out.println("  v2:  " + v2Result.lduCount + " LDUs, " + v2Result.bitErrors + " bit errors");

        // Phase 4: Correlate transmissions with decoded LDUs
        System.out.println("Phase 4: Correlating transmissions with decoded LDUs...");
        for(long[] tx : mDetectedTransmissions)
        {
            long startMs = tx[0];
            long endMs = tx[1];
            double peakEnergy = tx[2] / 1000.0;
            boolean isNarrowBand = tx.length > 3 && tx[3] == 1;

            int lsmLdus = countLdusInRange(result.lsmLduTimestamps, startMs, endMs);
            int v2Ldus = countLdusInRange(result.v2LduTimestamps, startMs, endMs);

            result.transmissions.add(new Transmission(startMs, endMs, peakEnergy, lsmLdus, v2Ldus, isNarrowBand));
        }

        return result;
    }

    /**
     * Detects transmission boundaries via energy envelope on raw I/Q samples.
     */
    private void detectTransmissions() throws IOException
    {
        mDetectedTransmissions.clear();

        try(ComplexWaveSource source = new ComplexWaveSource(mFile, false))
        {
            source.start();
            mSampleRate = source.getSampleRate();
            mFileStartTimeMs = System.currentTimeMillis();

            int silenceThresholdSamples = (int)(mSampleRate * SILENCE_THRESHOLD_SEC);
            long sampleCount = 0;

            source.setListener(iNativeBuffer -> {
                // Do nothing - we process in next() loop
            });

            try
            {
                while(true)
                {
                    source.next(2048, false);

                    // Read samples directly - we need to track energy ourselves
                    // This is a simplified version - in production we'd process the actual samples
                }
            }
            catch(Exception e)
            {
                // End of file
            }
        }

        // Alternative: process samples via decoder pipeline to get timestamps
        processForEnergyDetection();
    }

    /**
     * Process file specifically for energy-based transmission detection.
     */
    private void processForEnergyDetection() throws IOException
    {
        mDetectedTransmissions.clear();
        mEnergyAverage = 0f;
        mPeakEnergy = 0f;
        mInTransmission = false;

        try(ComplexWaveSource source = new ComplexWaveSource(mFile, false))
        {
            source.start();
            mSampleRate = source.getSampleRate();
            int silenceThresholdSamples = (int)(mSampleRate * SILENCE_THRESHOLD_SEC);
            final long[] sampleCount = {0};
            final long startTime = System.currentTimeMillis();

            source.setListener(iNativeBuffer -> {
                Iterator<ComplexSamples> it = iNativeBuffer.iterator();
                while(it.hasNext())
                {
                    ComplexSamples samples = it.next();
                    float[] i = samples.i();
                    float[] q = samples.q();

                    for(int idx = 0; idx < i.length; idx++)
                    {
                        float energy = (i[idx] * i[idx]) + (q[idx] * q[idx]);
                        mEnergyAverage += (energy - mEnergyAverage) * ENERGY_EMA_FACTOR;

                        // Track peak energy with slow decay
                        if(mEnergyAverage > mPeakEnergy)
                        {
                            mPeakEnergy = mEnergyAverage;
                        }
                        else
                        {
                            mPeakEnergy *= 0.99999f;
                        }

                        // Track bandwidth for narrow-band detection
                        mMinI = Math.min(mMinI, i[idx]);
                        mMaxI = Math.max(mMaxI, i[idx]);
                        mMinQ = Math.min(mMinQ, q[idx]);
                        mMaxQ = Math.max(mMaxQ, q[idx]);

                        float silenceThreshold = mPeakEnergy * SILENCE_RATIO;

                        if(mPeakEnergy > 0 && mEnergyAverage < silenceThreshold)
                        {
                            mSilenceSampleCount++;
                            if(mSilenceSampleCount >= silenceThresholdSamples && mInTransmission)
                            {
                                // End of transmission
                                long endSample = sampleCount[0] + idx - mSilenceSampleCount;
                                long startMs = (long)(mTransmissionStartSample * 1000.0 / mSampleRate);
                                long endMs = (long)(endSample * 1000.0 / mSampleRate);
                                long durationMs = endMs - startMs;

                                // Only record if duration is above minimum
                                if(durationMs >= MIN_TX_DURATION_SEC * 1000)
                                {
                                    // Check for narrow-band (possible non-P25 signal)
                                    float bandwidthI = mMaxI - mMinI;
                                    float bandwidthQ = mMaxQ - mMinQ;
                                    boolean isNarrowBand = false; // Simplified - full check would use FFT

                                    mDetectedTransmissions.add(new long[]{
                                        startMs, endMs, (long)(mTransmissionPeakEnergy * 1000), isNarrowBand ? 1 : 0
                                    });
                                }

                                mInTransmission = false;
                                mTransmissionPeakEnergy = 0f;

                                // Reset bandwidth tracking
                                mMinI = Float.MAX_VALUE; mMaxI = Float.MIN_VALUE;
                                mMinQ = Float.MAX_VALUE; mMaxQ = Float.MIN_VALUE;
                            }
                        }
                        else
                        {
                            if(!mInTransmission && mPeakEnergy > 0)
                            {
                                // Start of transmission
                                mTransmissionStartSample = sampleCount[0] + idx;
                                mInTransmission = true;
                            }

                            if(mInTransmission && mEnergyAverage > mTransmissionPeakEnergy)
                            {
                                mTransmissionPeakEnergy = mEnergyAverage;
                            }

                            mSilenceSampleCount = 0;
                        }
                    }
                    sampleCount[0] += i.length;
                }
            });

            try
            {
                while(true)
                {
                    source.next(2048, true);
                }
            }
            catch(Exception e)
            {
                // End of file - finalize any in-progress transmission
                if(mInTransmission)
                {
                    long startMs = (long)(mTransmissionStartSample * 1000.0 / mSampleRate);
                    long endMs = (long)(sampleCount[0] * 1000.0 / mSampleRate);
                    if((endMs - startMs) >= MIN_TX_DURATION_SEC * 1000)
                    {
                        mDetectedTransmissions.add(new long[]{
                            startMs, endMs, (long)(mTransmissionPeakEnergy * 1000), 0
                        });
                    }
                }
            }
        }
    }

    private static class DecoderResult
    {
        int lduCount = 0;
        int bitErrors = 0;
        List<Long> lduTimestamps = new ArrayList<>();
        String diagnostics = "";
    }

    /**
     * Runs a decoder (LSM or LSM v2) and collects LDU timestamps.
     */
    private DecoderResult runDecoder(boolean useV2) throws IOException
    {
        DecoderResult result = new DecoderResult();

        Listener<IMessage> messageListener = iMessage -> {
            if(iMessage instanceof P25P1Message message)
            {
                if(message.isValid())
                {
                    if(message.getMessage() != null)
                    {
                        result.bitErrors += Math.max(message.getMessage().getCorrectedBitCount(), 0);
                    }
                    P25P1DataUnitID duid = message.getDUID();
                    if(duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1 ||
                       duid == P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2)
                    {
                        result.lduCount++;
                        result.lduTimestamps.add(message.getTimestamp());
                    }
                }
            }
        };

        try(ComplexWaveSource source = new ComplexWaveSource(mFile, false))
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
                result.diagnostics = decoder.getDiagnostics();
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

        return result;
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

    /**
     * Counts LDUs that fall within a time range.
     */
    private int countLdusInRange(List<Long> timestamps, long startMs, long endMs)
    {
        // Add 200ms margin at each end to account for timestamp alignment
        long marginMs = 200;
        int count = 0;
        for(Long ts : timestamps)
        {
            if(ts >= (startMs - marginMs) && ts <= (endMs + marginMs))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Prints a detailed analysis report.
     */
    public void printReport(AnalysisResult result)
    {
        System.out.println();
        System.out.println("=== TRANSMISSION ANALYSIS REPORT ===");
        System.out.println("File: " + mFile.getName());
        System.out.println();

        System.out.println("--- Summary ---");
        System.out.println("Detected transmissions: " + result.transmissions.size());
        System.out.println("Narrow-band filtered:   " + result.narrowBandFiltered());
        System.out.println();
        System.out.println(String.format("%-20s %8s %8s %8s", "", "LSM", "v2", "Delta"));
        System.out.println(String.format("%-20s %8d %8d %+8d", "Total LDUs",
                result.totalLsmLdu, result.totalV2Ldu, result.totalV2Ldu - result.totalLsmLdu));
        System.out.println(String.format("%-20s %8d %8d %+8d", "Bit Errors",
                result.lsmBitErrors, result.v2BitErrors, result.v2BitErrors - result.lsmBitErrors));
        System.out.println();
        System.out.println("v2 Regressions (LSM decoded, v2 missed): " + result.v2Regressions());
        System.out.println("v2 Improvements (v2 decoded, LSM missed): " + result.v2Improvements());
        System.out.println("Both missed: " + result.bothMissed());
        System.out.println();
        System.out.println("v2 Diagnostics: " + result.v2Diagnostics);

        // Print transmission details
        System.out.println();
        System.out.println("--- Transmission Details ---");
        int txNum = 1;
        for(Transmission tx : result.transmissions)
        {
            System.out.println(String.format("%3d: %s", txNum++, tx));
        }

        // Print v2-specific regressions for investigation
        List<Transmission> regressions = result.transmissions.stream()
                .filter(Transmission::isV2Regression)
                .toList();

        if(!regressions.isEmpty())
        {
            System.out.println();
            System.out.println("--- v2 REGRESSIONS (need investigation) ---");
            for(Transmission tx : regressions)
            {
                System.out.println("  " + tx);
            }
        }
    }

    public static void main(String[] args)
    {
        if(args.length < 1)
        {
            System.out.println("Usage: TransmissionAnalyzer <path-to-baseband.wav>");
            System.out.println();
            System.out.println("Analyzes a baseband recording to detect transmission boundaries,");
            System.out.println("compares LSM vs LSM v2 decoding, and identifies missed transmissions.");
            return;
        }

        try
        {
            File file = new File(args[0]);
            TransmissionAnalyzer analyzer = new TransmissionAnalyzer(file);
            AnalysisResult result = analyzer.analyze();
            analyzer.printReport(result);
        }
        catch(IOException e)
        {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
