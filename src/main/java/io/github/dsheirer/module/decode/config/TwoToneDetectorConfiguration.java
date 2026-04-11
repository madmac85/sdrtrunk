package io.github.dsheirer.module.decode.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TwoToneDetectorConfiguration {
    private String mLabel = "New Detector";
    private double mToneAFrequency = 0.0;
    private int mToneADurationMs = 0;
    private boolean mLongTone = false;
    private double mToneBFrequency = 0.0;
    private int mToneBDurationMs = 0;
    private boolean mZelloAlertEnabled = false;
    private String mZelloChannel = "";
    private String mZelloAlertText = "";
    private boolean mUnknownToneLoggingEnabled = false;

    public String getLabel() {
        return mLabel;
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    public double getToneAFrequency() {
        return mToneAFrequency;
    }

    public void setToneAFrequency(double frequency) {
        mToneAFrequency = frequency;
    }

    public int getToneADurationMs() {
        return mToneADurationMs;
    }

    public void setToneADurationMs(int durationMs) {
        mToneADurationMs = durationMs;
    }

    public boolean isLongTone() {
        return mLongTone;
    }

    public void setLongTone(boolean longTone) {
        mLongTone = longTone;
    }

    public double getToneBFrequency() {
        return mToneBFrequency;
    }

    public void setToneBFrequency(double frequency) {
        mToneBFrequency = frequency;
    }

    public int getToneBDurationMs() {
        return mToneBDurationMs;
    }

    public void setToneBDurationMs(int durationMs) {
        mToneBDurationMs = durationMs;
    }

    public boolean isZelloAlertEnabled() {
        return mZelloAlertEnabled;
    }

    public void setZelloAlertEnabled(boolean enabled) {
        mZelloAlertEnabled = enabled;
    }

    public String getZelloChannel() {
        return mZelloChannel;
    }

    public void setZelloChannel(String channel) {
        mZelloChannel = channel;
    }

    public String getZelloAlertText() {
        return mZelloAlertText;
    }

    public void setZelloAlertText(String alertText) {
        mZelloAlertText = alertText;
    }

    public boolean isUnknownToneLoggingEnabled() {
        return mUnknownToneLoggingEnabled;
    }

    public void setUnknownToneLoggingEnabled(boolean enabled) {
        mUnknownToneLoggingEnabled = enabled;
    }
}
