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

import io.github.dsheirer.buffer.INativeBuffer;
import io.github.dsheirer.buffer.FloatNativeBuffer;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.ConversionUtils;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Modified ComplexWaveSource for testing that provides timestamps based on sample position
 * within the file rather than wall-clock time. This enables accurate correlation between
 * decoded message timestamps and transmission boundaries detected from energy analysis.
 */
public class TestComplexWaveSource implements AutoCloseable
{
    private AudioInputStream mInputStream;
    private Listener<INativeBuffer> mListener;
    private final File mFile;
    private int mBytesPerFrame;
    private double mSampleRate;
    private long mSampleCount = 0;

    public TestComplexWaveSource(File file) throws IOException, UnsupportedAudioFileException
    {
        mFile = file;
        open();
    }

    private void open() throws IOException, UnsupportedAudioFileException
    {
        mInputStream = AudioSystem.getAudioInputStream(mFile);
        AudioFormat format = mInputStream.getFormat();
        mBytesPerFrame = format.getFrameSize();
        mSampleRate = format.getSampleRate();

        // Verify format: 2 channels (I/Q), 16-bit samples
        if(format.getChannels() != 2 || format.getSampleSizeInBits() != 16)
        {
            throw new IOException("Unsupported Wave Format - EXPECTED: 2 channels 16-bit samples FOUND: " +
                format.getChannels() + " channels " + format.getSampleSizeInBits() + "-bit samples");
        }
    }

    public void setListener(Listener<INativeBuffer> listener)
    {
        mListener = listener;
    }

    public double getSampleRate()
    {
        return mSampleRate;
    }

    /**
     * Reads and processes the next batch of samples from the file.
     *
     * @param frames number of sample frames to read
     * @return true if samples were read, false if end of file
     */
    public boolean next(int frames) throws IOException
    {
        if(mInputStream == null || mListener == null)
        {
            return false;
        }

        byte[] buffer = new byte[mBytesPerFrame * frames];
        int bytesRead = mInputStream.read(buffer);

        if(bytesRead <= 0)
        {
            return false;
        }

        int samplesRead = bytesRead / mBytesPerFrame;

        // Calculate timestamp based on sample position, not wall clock
        // This gives timestamps in milliseconds from start of file
        long timestamp = (long)((mSampleCount / mSampleRate) * 1000.0);

        mSampleCount += samplesRead;

        float[] samples = ConversionUtils.convertFromSigned16BitSamples(buffer);
        mListener.receive(new FloatNativeBuffer(samples, timestamp, (float)(mSampleRate / 1000.0)));

        return true;
    }

    @Override
    public void close() throws IOException
    {
        if(mInputStream != null)
        {
            mInputStream.close();
            mInputStream = null;
        }
    }
}
