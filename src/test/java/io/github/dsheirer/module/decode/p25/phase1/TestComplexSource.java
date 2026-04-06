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

import io.github.dsheirer.sample.ConversionUtils;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.SampleUtils;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.ComplexSource;
import io.github.dsheirer.source.SourceEvent;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * ComplexSource implementation that reads baseband WAV files and delivers ComplexSamples
 * with sample-position-based timestamps. Designed for use with ProcessingChain to replay
 * recorded baseband through the full live pipeline for diagnostic purposes.
 */
public class TestComplexSource extends ComplexSource implements AutoCloseable
{
    private AudioInputStream mInputStream;
    private Listener<ComplexSamples> mListener;
    private final File mFile;
    private int mBytesPerFrame;
    private double mSampleRate;
    private long mFrequency;
    private long mSampleCount = 0;

    public TestComplexSource(File file, long frequency) throws IOException, UnsupportedAudioFileException
    {
        mFile = file;
        mFrequency = frequency;
        open();
    }

    private void open() throws IOException, UnsupportedAudioFileException
    {
        mInputStream = AudioSystem.getAudioInputStream(mFile);
        AudioFormat format = mInputStream.getFormat();
        mBytesPerFrame = format.getFrameSize();
        mSampleRate = format.getSampleRate();

        if(format.getChannels() != 2 || format.getSampleSizeInBits() != 16)
        {
            throw new IOException("Unsupported Wave Format - EXPECTED: 2 channels 16-bit FOUND: " +
                format.getChannels() + " channels " + format.getSampleSizeInBits() + "-bit");
        }
    }

    @Override
    public void setListener(Listener<ComplexSamples> listener)
    {
        mListener = listener;
    }

    public Listener<ComplexSamples> getListener()
    {
        return mListener;
    }

    public void removeListener(Listener<ComplexSamples> listener)
    {
        mListener = null;
    }

    @Override
    public double getSampleRate()
    {
        return mSampleRate;
    }

    @Override
    public long getFrequency()
    {
        return mFrequency;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}

    @Override
    public Listener<SourceEvent> getSourceEventListener()
    {
        return null;
    }

    @Override
    public void setSourceEventListener(Listener<SourceEvent> listener) {}

    @Override
    public void removeSourceEventListener() {}

    /**
     * Reads and delivers the next batch of samples. Returns false at EOF.
     */
    public boolean next(int frames) throws IOException
    {
        if(mInputStream == null || mListener == null) return false;

        byte[] buffer = new byte[mBytesPerFrame * frames];
        int bytesRead = mInputStream.read(buffer);
        if(bytesRead <= 0) return false;

        int samplesRead = bytesRead / mBytesPerFrame;
        long timestamp = (long)((mSampleCount / mSampleRate) * 1000.0);
        mSampleCount += samplesRead;

        // Convert to interleaved floats, then deinterleave to ComplexSamples
        float[] interleaved = ConversionUtils.convertFromSigned16BitSamples(buffer);
        ComplexSamples cs = SampleUtils.deinterleave(interleaved, timestamp);
        mListener.receive(cs);
        return true;
    }

    public void close() throws IOException
    {
        if(mInputStream != null)
        {
            mInputStream.close();
            mInputStream = null;
        }
    }
}
