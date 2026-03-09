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
package io.github.dsheirer.module.decode.nbfm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.dsp.squelch.NoiseSquelch;
import io.github.dsheirer.dsp.squelch.SquelchTailRemover;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.analog.DecodeConfigAnalog;
import io.github.dsheirer.module.decode.config.ChannelToneFilter;
import io.github.dsheirer.source.tuner.channel.ChannelSpecification;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder configuration for an NBFM channel.
 *
 * Supports channel-level CTCSS/DCS tone filtering and squelch tail removal.
 */
public class DecodeConfigNBFM extends DecodeConfigAnalog
{
    private boolean mAudioFilter = true;
    private float mSquelchNoiseOpenThreshold = NoiseSquelch.DEFAULT_NOISE_OPEN_THRESHOLD;
    private float mSquelchNoiseCloseThreshold = NoiseSquelch.DEFAULT_NOISE_CLOSE_THRESHOLD;
    private int mSquelchHysteresisOpenThreshold = NoiseSquelch.DEFAULT_HYSTERESIS_OPEN_THRESHOLD;
    private int mSquelchHysteresisCloseThreshold = NoiseSquelch.DEFAULT_HYSTERESIS_CLOSE_THRESHOLD;

    // === NEW: Channel-level tone filtering ===
    private List<ChannelToneFilter> mToneFilters = new ArrayList<>();
    private boolean mToneFilterEnabled = false;

    // === NEW: Squelch tail/head removal ===
    private int mSquelchTailRemovalMs = SquelchTailRemover.DEFAULT_TAIL_REMOVAL_MS;
    private int mSquelchHeadRemovalMs = SquelchTailRemover.DEFAULT_HEAD_REMOVAL_MS;
    private boolean mSquelchTailRemovalEnabled = false;

    /**
     * Constructs an instance
     */
    public DecodeConfigNBFM()
    {
    }

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance")
    public DecoderType getDecoderType()
    {
        return DecoderType.NBFM;
    }

    @Override
    protected Bandwidth getDefaultBandwidth()
    {
        return Bandwidth.BW_12_5;
    }

    /**
     * Channel sample stream specification.
     */
    @JsonIgnore
    @Override
    public ChannelSpecification getChannelSpecification()
    {
        switch(getBandwidth())
        {
            case BW_7_5:
                return new ChannelSpecification(25000.0, 7500, 3500.0, 3750.0);
            case BW_12_5:
                return new ChannelSpecification(25000.0, 12500, 6000.0, 7000.0);
            case BW_25_0:
                return new ChannelSpecification(50000.0, 25000, 12500.0, 13500.0);
            default:
                throw new IllegalArgumentException("Unrecognized FM bandwidth value: " + getBandwidth());
        }
    }

    // ========== Existing squelch configuration ==========

    @JacksonXmlProperty(isAttribute = true, localName = "audioFilter")
    public boolean isAudioFilter()
    {
        return mAudioFilter;
    }

    public void setAudioFilter(boolean audioFilter)
    {
        mAudioFilter = audioFilter;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "squelchNoiseOpenThreshold")
    public float getSquelchNoiseOpenThreshold()
    {
        return mSquelchNoiseOpenThreshold;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "squelchNoiseCloseThreshold")
    public float getSquelchNoiseCloseThreshold()
    {
        return mSquelchNoiseCloseThreshold;
    }

    public void setSquelchNoiseOpenThreshold(float open)
    {
        if(open < NoiseSquelch.MINIMUM_NOISE_THRESHOLD || open > NoiseSquelch.MAXIMUM_NOISE_THRESHOLD)
        {
            throw new IllegalArgumentException("Squelch noise open threshold is out of range: " + open);
        }
        mSquelchNoiseOpenThreshold = open;
    }

    public void setSquelchNoiseCloseThreshold(float close)
    {
        if(close < NoiseSquelch.MINIMUM_NOISE_THRESHOLD || close > NoiseSquelch.MAXIMUM_NOISE_THRESHOLD)
        {
            throw new IllegalArgumentException("Squelch noise close threshold is out of range: " + close);
        }
        mSquelchNoiseCloseThreshold = close;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "squelchHysteresisOpenThreshold")
    public int getSquelchHysteresisOpenThreshold()
    {
        return mSquelchHysteresisOpenThreshold;
    }

    public void setSquelchHysteresisOpenThreshold(int open)
    {
        if(open < NoiseSquelch.MINIMUM_HYSTERESIS_THRESHOLD || open > NoiseSquelch.MAXIMUM_HYSTERESIS_THRESHOLD)
        {
            throw new IllegalArgumentException("Squelch hysteresis open threshold is out of range: " + open);
        }
        mSquelchHysteresisOpenThreshold = open;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "squelchHysteresisCloseThreshold")
    public int getSquelchHysteresisCloseThreshold()
    {
        return mSquelchHysteresisCloseThreshold;
    }

    public void setSquelchHysteresisCloseThreshold(int close)
    {
        if(close < NoiseSquelch.MINIMUM_HYSTERESIS_THRESHOLD || close > NoiseSquelch.MAXIMUM_HYSTERESIS_THRESHOLD)
        {
            throw new IllegalArgumentException("Squelch hysteresis close threshold is out of range: " + close);
        }
        mSquelchHysteresisCloseThreshold = close;
    }

    // ========== NEW: Channel-level tone filtering ==========

    /**
     * List of CTCSS/DCS tone filters for this channel. When enabled, audio is only passed
     * when the received signal matches at least one of the configured tones.
     * Empty list with filtering enabled means no audio passes (muted).
     * Filtering disabled means all audio passes (backward compatible).
     */
    @JacksonXmlElementWrapper(localName = "toneFilters")
    @JacksonXmlProperty(localName = "toneFilter")
    public List<ChannelToneFilter> getToneFilters()
    {
        return mToneFilters;
    }

    public void setToneFilters(List<ChannelToneFilter> toneFilters)
    {
        mToneFilters = toneFilters != null ? toneFilters : new ArrayList<>();
    }

    /**
     * Adds a tone filter to the channel configuration
     */
    public void addToneFilter(ChannelToneFilter filter)
    {
        if(filter != null)
        {
            mToneFilters.add(filter);
        }
    }

    /**
     * Removes a tone filter from the channel configuration
     */
    public void removeToneFilter(ChannelToneFilter filter)
    {
        mToneFilters.remove(filter);
    }

    /**
     * Indicates if tone filtering is enabled for this channel
     */
    @JacksonXmlProperty(isAttribute = true, localName = "toneFilterEnabled")
    public boolean isToneFilterEnabled()
    {
        return mToneFilterEnabled;
    }

    public void setToneFilterEnabled(boolean enabled)
    {
        mToneFilterEnabled = enabled;
    }

    /**
     * Indicates if this channel has valid, enabled tone filters configured
     */
    @JsonIgnore
    public boolean hasToneFiltering()
    {
        return mToneFilterEnabled && !mToneFilters.isEmpty();
    }

    // ========== NEW: Squelch tail/head removal ==========

    /**
     * Squelch tail removal enabled state. When enabled, the configured number of
     * milliseconds are trimmed from the end of each transmission to remove the
     * noise burst that occurs when the transmitter drops carrier.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "squelchTailRemovalEnabled")
    public boolean isSquelchTailRemovalEnabled()
    {
        return mSquelchTailRemovalEnabled;
    }

    public void setSquelchTailRemovalEnabled(boolean enabled)
    {
        mSquelchTailRemovalEnabled = enabled;
    }

    /**
     * Milliseconds to trim from end of transmission (squelch tail removal).
     * Range: 0-300ms. Default: 100ms.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "squelchTailRemovalMs")
    public int getSquelchTailRemovalMs()
    {
        return mSquelchTailRemovalMs;
    }

    public void setSquelchTailRemovalMs(int ms)
    {
        mSquelchTailRemovalMs = Math.max(SquelchTailRemover.MINIMUM_REMOVAL_MS,
                Math.min(SquelchTailRemover.MAXIMUM_TAIL_REMOVAL_MS, ms));
    }

    /**
     * Milliseconds to trim from start of transmission (squelch head removal).
     * Useful for removing CTCSS tone ramp-up noise. Range: 0-150ms. Default: 0ms.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "squelchHeadRemovalMs")
    public int getSquelchHeadRemovalMs()
    {
        return mSquelchHeadRemovalMs;
    }

    public void setSquelchHeadRemovalMs(int ms)
    {
        mSquelchHeadRemovalMs = Math.max(SquelchTailRemover.MINIMUM_REMOVAL_MS,
                Math.min(SquelchTailRemover.MAXIMUM_HEAD_REMOVAL_MS, ms));
    }

    // ========== VOXSEND AUDIO FILTER CONFIGURATION ==========
    private boolean mDeemphasisEnabled = true;
    private double mDeemphasisTimeConstant = 75.0;
    private boolean mLowPassEnabled = true;
    private double mLowPassCutoff = 3400.0;
    private boolean mBassBoostEnabled = false;
    private float mBassBoostDb = 0.0f;
    private boolean mNoiseGateEnabled = false;
    private float mNoiseGateThreshold = 4.0f;
    private float mNoiseGateReduction = 0.8f;
    private int mNoiseGateHoldTime = 500;
    private boolean mAgcEnabled = true;
    private float mAgcTargetLevel = -18.0f;
    private float mAgcMaxGain = 24.0f;

    @JacksonXmlProperty(isAttribute = true, localName = "deemphasisEnabled")
    public boolean isDeemphasisEnabled() { return mDeemphasisEnabled; }
    public void setDeemphasisEnabled(boolean enabled) { mDeemphasisEnabled = enabled; }
    @JacksonXmlProperty(isAttribute = true, localName = "deemphasisTimeConstant")
    public double getDeemphasisTimeConstant() { return mDeemphasisTimeConstant; }
    public void setDeemphasisTimeConstant(double tc) { mDeemphasisTimeConstant = tc; }
    @JacksonXmlProperty(isAttribute = true, localName = "lowPassEnabled")
    public boolean isLowPassEnabled() { return mLowPassEnabled; }
    public void setLowPassEnabled(boolean enabled) { mLowPassEnabled = enabled; }
    @JacksonXmlProperty(isAttribute = true, localName = "lowPassCutoff")
    public double getLowPassCutoff() { return mLowPassCutoff; }
    public void setLowPassCutoff(double cutoff) { mLowPassCutoff = cutoff; }
    @JacksonXmlProperty(isAttribute = true, localName = "bassBoostEnabled")
    public boolean isBassBoostEnabled() { return mBassBoostEnabled; }
    public void setBassBoostEnabled(boolean enabled) { mBassBoostEnabled = enabled; }
    @JacksonXmlProperty(isAttribute = true, localName = "bassBoostDb")
    public float getBassBoostDb() { return mBassBoostDb; }
    public void setBassBoostDb(float db) { mBassBoostDb = Math.max(0.0f, Math.min(12.0f, db)); }
    @JacksonXmlProperty(isAttribute = true, localName = "noiseGateEnabled")
    public boolean isNoiseGateEnabled() { return mNoiseGateEnabled; }
    public void setNoiseGateEnabled(boolean enabled) { mNoiseGateEnabled = enabled; }
    @JacksonXmlProperty(isAttribute = true, localName = "noiseGateThreshold")
    public float getNoiseGateThreshold() { return mNoiseGateThreshold; }
    public void setNoiseGateThreshold(float t) { mNoiseGateThreshold = Math.max(0.0f, Math.min(100.0f, t)); }
    @JacksonXmlProperty(isAttribute = true, localName = "noiseGateReduction")
    public float getNoiseGateReduction() { return mNoiseGateReduction; }
    public void setNoiseGateReduction(float r) { mNoiseGateReduction = Math.max(0.0f, Math.min(1.0f, r)); }
    @JacksonXmlProperty(isAttribute = true, localName = "noiseGateHoldTime")
    public int getNoiseGateHoldTime() { return mNoiseGateHoldTime; }
    public void setNoiseGateHoldTime(int ms) { mNoiseGateHoldTime = Math.max(0, Math.min(1000, ms)); }
    @JacksonXmlProperty(isAttribute = true, localName = "agcEnabled")
    public boolean isAgcEnabled() { return mAgcEnabled; }
    public void setAgcEnabled(boolean enabled) { mAgcEnabled = enabled; }
    @JacksonXmlProperty(isAttribute = true, localName = "agcTargetLevel")
    public float getAgcTargetLevel() { return mAgcTargetLevel; }
    public void setAgcTargetLevel(float level) { mAgcTargetLevel = level; }
    @JacksonXmlProperty(isAttribute = true, localName = "agcMaxGain")
    public float getAgcMaxGain() { return mAgcMaxGain; }
    public void setAgcMaxGain(float gain) { mAgcMaxGain = gain; }
}
