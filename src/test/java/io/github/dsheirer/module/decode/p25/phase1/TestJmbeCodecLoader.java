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

import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioCodecLibrary;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Standalone JMBE codec loader for test use that doesn't depend on UserPreferences.
 * Loads the JMBE library from a specified path and provides access to the IMBE codec.
 */
public class TestJmbeCodecLoader
{
    private static final String CODEC_NAME = "IMBE";
    private IAudioCodec mImbeCodec;
    private String mVersion;
    private boolean mLoaded = false;
    private String mLoadError;

    /**
     * Constructs a JMBE codec loader from the specified path.
     * @param jmbePath path to the JMBE JAR file
     */
    public TestJmbeCodecLoader(Path jmbePath)
    {
        loadCodec(jmbePath);
    }

    /**
     * Loads the JMBE library and IMBE codec from the specified path.
     */
    private void loadCodec(Path jmbePath)
    {
        if(jmbePath == null || !jmbePath.toFile().exists())
        {
            mLoadError = "JMBE library path is null or does not exist: " + jmbePath;
            return;
        }

        try
        {
            URLClassLoader loader = new URLClassLoader(
                new URL[]{jmbePath.toUri().toURL()},
                this.getClass().getClassLoader()
            );

            Class<?> libClass = Class.forName("jmbe.JMBEAudioLibrary", true, loader);
            Object instance = libClass.getDeclaredConstructor().newInstance();

            if(instance instanceof IAudioCodecLibrary library)
            {
                // Check version: require 1.0.0 or higher
                if((library.getMajorVersion() == 1 && library.getMinorVersion() >= 0 &&
                    library.getBuildVersion() >= 0) || library.getMajorVersion() >= 1)
                {
                    mImbeCodec = library.getAudioConverter(CODEC_NAME);
                    mVersion = library.getVersion();
                    mLoaded = true;
                }
                else
                {
                    mLoadError = "JMBE library version 1.0.0 or higher required, found: " + library.getVersion();
                }
            }
            else
            {
                mLoadError = "Loaded class is not an IAudioCodecLibrary";
            }
        }
        catch(Exception e)
        {
            mLoadError = "Failed to load JMBE library: " + e.getMessage();
        }
    }

    /**
     * Indicates if the codec was successfully loaded.
     */
    public boolean isLoaded()
    {
        return mLoaded;
    }

    /**
     * Returns any error message from loading, or null if successful.
     */
    public String getLoadError()
    {
        return mLoadError;
    }

    /**
     * Returns the JMBE library version string.
     */
    public String getVersion()
    {
        return mVersion;
    }

    /**
     * Decodes an IMBE frame to audio samples.
     * @param imbeFrame the 18-byte (144-bit) IMBE frame
     * @return decoded audio samples (160 samples @ 8 kHz), or null if codec not loaded
     */
    public float[] decodeFrame(byte[] imbeFrame)
    {
        if(mImbeCodec == null)
        {
            return null;
        }
        return mImbeCodec.getAudio(imbeFrame);
    }

    /**
     * Resets the codec state (clears any inter-frame data).
     */
    public void reset()
    {
        if(mImbeCodec != null)
        {
            mImbeCodec.reset();
        }
    }

    /**
     * Returns the underlying audio codec, or null if not loaded.
     */
    public IAudioCodec getCodec()
    {
        return mImbeCodec;
    }
}
