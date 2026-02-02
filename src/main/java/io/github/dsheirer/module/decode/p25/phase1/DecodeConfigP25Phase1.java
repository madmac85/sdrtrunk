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
     * Increased to 500ms to better handle sync loss during active transmissions.
     */
    public static final int MAX_AUDIO_HOLDOVER_MS = 500;

    /**
     * Audio frame concealment strategy.
     */
    public enum AudioConcealmentStrategy
    {
        /** No concealment - output decoded audio as-is (may contain artifacts) */
        NONE,
        /** Repeat the last good frame when corruption is detected */
        REPEAT_LAST,
        /** Insert silence when corruption is detected */
        SILENCE
    }

    private Modulation mModulation = Modulation.C4FM;
    private int mConfiguredNAC = 0; // 0 = auto-detect, 1-4095 = configured NAC
    private int mAudioHoldoverMs = DEFAULT_AUDIO_HOLDOVER_MS;
    private boolean mIgnoreEncryptionState = false;
    private boolean mIgnoreControlChannelState = false;
    private AudioConcealmentStrategy mAudioConcealment = AudioConcealmentStrategy.REPEAT_LAST;

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
     * Indicates if control channel detection should be ignored for this channel.
     * When true, the decoder will never transition to CONTROL state, even if control
     * channel messages are detected. Use this for dedicated voice channels that are
     * known to never carry control channel traffic, to prevent false detections caused
     * by decode errors producing garbage data that resembles control messages.
     *
     * @return true if control channel detection should be bypassed
     */
    @JacksonXmlProperty(isAttribute = true, localName = "ignoreControlChannelState")
    public boolean isIgnoreControlChannelState()
    {
        return mIgnoreControlChannelState;
    }

    /**
     * Sets whether control channel detection should be ignored.
     *
     * @param ignore true to bypass control channel detection
     */
    public void setIgnoreControlChannelState(boolean ignore)
    {
        mIgnoreControlChannelState = ignore;
    }

    /**
     * Gets the audio frame concealment strategy.
     * Concealment replaces corrupted audio frames with either silence or a repeated
     * previous good frame to reduce audible artifacts.
     *
     * @return the concealment strategy
     */
    @JacksonXmlProperty(isAttribute = true, localName = "audioConcealment")
    public AudioConcealmentStrategy getAudioConcealment()
    {
        return mAudioConcealment;
    }

    /**
     * Sets the audio frame concealment strategy.
     *
     * @param strategy the concealment strategy to use
     */
    public void setAudioConcealment(AudioConcealmentStrategy strategy)
    {
        mAudioConcealment = strategy != null ? strategy : AudioConcealmentStrategy.REPEAT_LAST;
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
