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


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.source.tuner.channel.ChannelSpecification;

/**
 * APCO25 Phase 1 decoder configuration
 */
public class DecodeConfigP25Phase1 extends DecodeConfigP25
{
    public static final int CHANNEL_ROTATION_DELAY_MINIMUM_MS = 400;
    public static final int CHANNEL_ROTATION_DELAY_DEFAULT_MS = 500;
    public static final int CHANNEL_ROTATION_DELAY_MAXIMUM_MS = 2000;
    public static final int NAC_MINIMUM = 0;
    public static final int NAC_MAXIMUM = 4095;

    /**
     * Default audio holdover period in milliseconds (approximately 1 LDU frame duration)
     */
    public static final int DEFAULT_AUDIO_HOLDOVER_MS = 180;

    /**
     * Maximum audio holdover period in milliseconds.
     * Increased to 1000ms to support simulcast channels where valid LDUs may arrive at longer intervals
     * due to multipath NID corruption.
     */
    public static final int MAX_AUDIO_HOLDOVER_MS = 1000;

    /**
     * Maximum IMBE errors before quality gate activates.
     */
    public static final int MAX_IMBE_ERRORS_MINIMUM = 0;
    public static final int MAX_IMBE_ERRORS_MAXIMUM = 10;

    /**
     * BCH error correction threshold for NAC-assisted NID decode.
     */
    public static final int MAX_BCH_ERRORS_MINIMUM = 1;
    public static final int MAX_BCH_ERRORS_MAXIMUM = 11;
    public static final int MAX_BCH_ERRORS_DEFAULT = 5;

    private Modulation mModulation = Modulation.C4FM;
    private int mConfiguredNAC = 0; // 0 = auto-detect, 1-4095 = configured NAC
    private int mAudioHoldoverMs = DEFAULT_AUDIO_HOLDOVER_MS;
    private boolean mIgnoreEncryptionState = false;
    private int mMaxImbeErrors = 0; // 0 = disabled, 1-10 = quality gate threshold
    private int mMaxBchErrors = MAX_BCH_ERRORS_DEFAULT;

    /**
     * Constructs an instance
     */
    public DecodeConfigP25Phase1()
    {
    }

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance")
    public DecoderType getDecoderType()
    {
        return DecoderType.P25_PHASE1;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "modulation")
    public Modulation getModulation()
    {
        return mModulation;
    }

    public void setModulation(Modulation modulation)
    {
        mModulation = modulation;
    }

    /**
     * Gets the configured NAC (Network Access Code) for this channel.
     * When set to a non-zero value (1-4095), the decoder will use this NAC for improved NID error correction.
     * When set to 0 (default), the decoder will auto-detect the NAC from received transmissions.
     *
     * @return configured NAC value, or 0 for auto-detect
     */
    @JacksonXmlProperty(isAttribute = true, localName = "configuredNAC")
    public int getConfiguredNAC()
    {
        return mConfiguredNAC;
    }

    /**
     * Sets the configured NAC (Network Access Code) for this channel.
     * Set to a value between 1-4095 to use a known NAC for improved error correction.
     * Set to 0 to auto-detect the NAC from received transmissions.
     *
     * @param nac the NAC value (0-4095)
     */
    public void setConfiguredNAC(int nac)
    {
        if(nac < NAC_MINIMUM || nac > NAC_MAXIMUM)
        {
            throw new IllegalArgumentException("NAC must be between " + NAC_MINIMUM + " and " + NAC_MAXIMUM);
        }
        mConfiguredNAC = nac;
    }

    /**
     * Indicates if a NAC is configured for this channel.
     * @return true if a NAC is configured (non-zero)
     */
    @JsonIgnore
    public boolean hasConfiguredNAC()
    {
        return mConfiguredNAC > 0;
    }

    /**
     * Gets the audio holdover period in milliseconds. During decode errors, if RF signal energy
     * indicates an active transmission is still present, the decoder will maintain the call state
     * for up to this duration before ending the audio segment.
     *
     * @return holdover period in milliseconds (0 to MAX_AUDIO_HOLDOVER_MS)
     */
    @JacksonXmlProperty(isAttribute = true, localName = "audioHoldoverMs")
    public int getAudioHoldoverMs()
    {
        return mAudioHoldoverMs;
    }

    /**
     * Sets the audio holdover period in milliseconds. When set to 0, holdover is disabled.
     * Values above MAX_AUDIO_HOLDOVER_MS will be clamped to the maximum.
     *
     * @param holdoverMs the holdover period in milliseconds (0 to MAX_AUDIO_HOLDOVER_MS)
     */
    public void setAudioHoldoverMs(int holdoverMs)
    {
        if(holdoverMs < 0)
        {
            mAudioHoldoverMs = 0;
        }
        else if(holdoverMs > MAX_AUDIO_HOLDOVER_MS)
        {
            mAudioHoldoverMs = MAX_AUDIO_HOLDOVER_MS;
        }
        else
        {
            mAudioHoldoverMs = holdoverMs;
        }
    }

    /**
     * Indicates if encryption detection should be ignored for this channel.
     * When true, the decoder will assume all calls are unencrypted and begin audio processing
     * immediately without waiting to verify encryption state. Use this for dedicated voice
     * channels that are known to never carry encrypted traffic.
     *
     * @return true if encryption detection should be bypassed
     */
    @JacksonXmlProperty(isAttribute = true, localName = "ignoreEncryptionState")
    public boolean isIgnoreEncryptionState()
    {
        return mIgnoreEncryptionState;
    }

    /**
     * Sets whether encryption detection should be ignored.
     *
     * @param ignore true to bypass encryption detection and assume unencrypted
     */
    public void setIgnoreEncryptionState(boolean ignore)
    {
        mIgnoreEncryptionState = ignore;
    }

    /**
     * Gets the maximum IMBE FEC errors allowed per frame before the quality gate activates.
     * Frames exceeding this threshold are replaced with silence or frame repetition before
     * being sent to the JMBE codec, preventing codec state contamination.
     * Set to 0 to disable the quality gate (default).
     *
     * @return max IMBE errors threshold (0 = disabled, recommended: 3 for simulcast)
     */
    @JacksonXmlProperty(isAttribute = true, localName = "maxImbeErrors")
    public int getMaxImbeErrors()
    {
        return mMaxImbeErrors;
    }

    /**
     * Sets the maximum IMBE FEC errors per frame for the pre-codec quality gate.
     * Set to 0 to disable. Values 1-10 enable the gate at that threshold.
     *
     * @param maxErrors maximum total FEC errors per IMBE frame (0 = disabled)
     */
    public void setMaxImbeErrors(int maxErrors)
    {
        mMaxImbeErrors = Math.max(MAX_IMBE_ERRORS_MINIMUM, Math.min(MAX_IMBE_ERRORS_MAXIMUM, maxErrors));
    }

    /**
     * Gets the maximum BCH error corrections allowed for NAC-assisted NID decode.
     * Controls how many bit corrections are accepted when using configured NAC and DUID enumeration.
     * Lower values reject more aggressively (filtering corrupted frames), higher values accept more corrections.
     *
     * @return max BCH errors threshold (1-11, default 5)
     */
    @JacksonXmlProperty(isAttribute = true, localName = "maxBchErrors")
    public int getMaxBchErrors()
    {
        return mMaxBchErrors;
    }

    /**
     * Sets the maximum BCH error corrections for NAC-assisted NID decode.
     *
     * @param maxErrors maximum BCH corrections allowed (1-11)
     */
    public void setMaxBchErrors(int maxErrors)
    {
        mMaxBchErrors = Math.max(MAX_BCH_ERRORS_MINIMUM, Math.min(MAX_BCH_ERRORS_MAXIMUM, maxErrors));
    }

    /**
     * Source channel specification for this decoder
     */
    @JsonIgnore
    @Override
    public ChannelSpecification getChannelSpecification()
    {
        return new ChannelSpecification(50000.0, 12500, 5750.0, 6500.0);
    }
}
