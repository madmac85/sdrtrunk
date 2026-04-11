package io.github.dsheirer.module.decode.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.dsheirer.dsp.filter.GoertzelFilter;
import io.github.dsheirer.dsp.window.WindowType;
import java.util.ArrayList;
import java.util.List;

public class TwoToneDetector {
    private static final Logger mLog = LoggerFactory.getLogger(TwoToneDetector.class);
    private TwoToneDetectorConfiguration mConfiguration;
    private float mSampleRate;
    private List<ToneDetectorListener> mListeners = new ArrayList<>();
    private GoertzelFilter mToneAFilter;
    private GoertzelFilter mToneBFilter;
    private float[] mSampleBuffer;
    private int mSampleIndex = 0;
    private int mBlockSize;
    private int mToneAConfirmations = 0;
    private int mToneBConfirmations = 0;
    private boolean mToneAActive = false;
    private long mLastToneAMatchTime = 0;

    private float[] mUnknownToneFrequencies;
    private GoertzelFilter[] mUnknownToneFilters;
    private int[] mUnknownToneConfirmations;
    private int mUnknownToneThreshold = 3;

    public interface ToneDetectorListener {
        void onToneDetected(TwoToneDetectorConfiguration configuration);
    }

    public TwoToneDetector(TwoToneDetectorConfiguration configuration, float sampleRate) {
        mConfiguration = configuration;
        mSampleRate = sampleRate;
        mBlockSize = (int) (sampleRate / 10.0f);
        if (mBlockSize < 256) mBlockSize = 256;
        mSampleBuffer = new float[mBlockSize];
        if (configuration.getToneAFrequency() > 0) {
            mToneAFilter = new GoertzelFilter((int) sampleRate, (long) configuration.getToneAFrequency(), mBlockSize, WindowType.HAMMING);
        }
        if (configuration.getToneBFrequency() > 0 && !configuration.isLongTone()) {
            mToneBFilter = new GoertzelFilter((int) sampleRate, (long) configuration.getToneBFrequency(), mBlockSize, WindowType.HAMMING);
        }
        if (configuration.isUnknownToneLoggingEnabled()) {
            mUnknownToneFrequencies = new float[] {
                321.7f, 339.6f, 349.0f, 358.6f, 368.5f, 378.6f, 389.0f, 399.8f, 410.8f, 422.1f, 433.7f, 445.7f,
                457.9f, 470.5f, 483.5f, 496.8f, 510.5f, 524.6f, 539.0f, 553.9f, 569.1f, 584.8f, 600.9f, 617.4f,
                634.5f, 651.9f, 669.9f, 688.3f, 707.3f, 726.8f, 746.8f, 767.4f, 788.5f, 810.2f, 832.5f, 855.5f,
                879.0f, 903.2f, 928.1f, 953.7f, 979.9f, 1006.9f, 1034.7f, 1063.2f, 1092.4f, 1122.5f, 1153.4f,
                1185.2f, 1217.8f, 1251.4f, 1285.8f, 1321.2f, 1357.6f, 1395.0f, 1433.4f, 1472.9f, 1513.5f, 1555.2f,
                1598.0f, 1642.0f, 1687.2f, 1733.7f, 1781.5f, 1830.5f, 1881.0f, 1932.8f, 1986.0f, 2040.7f, 2096.9f,
                2154.6f, 2213.8f, 2274.5f, 2336.7f, 2400.3f, 2465.5f, 2532.5f, 2601.1f, 2671.6f, 2744.0f, 2818.4f,
                2894.9f, 2973.4f, 3054.1f, 3137.0f, 3222.1f, 3309.5f, 3399.2f, 3491.3f
            };
            mUnknownToneFilters = new GoertzelFilter[mUnknownToneFrequencies.length];
            mUnknownToneConfirmations = new int[mUnknownToneFrequencies.length];
            for (int i = 0; i < mUnknownToneFrequencies.length; i++) {
                mUnknownToneFilters[i] = new GoertzelFilter((int) sampleRate, (long) mUnknownToneFrequencies[i], mBlockSize, WindowType.HAMMING);
                mUnknownToneConfirmations[i] = 0;
            }
        }
    }

    public TwoToneDetectorConfiguration getConfiguration() {
        return mConfiguration;
    }

    public void addListener(ToneDetectorListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ToneDetectorListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (ToneDetectorListener listener : mListeners) {
            listener.onToneDetected(mConfiguration);
        }
    }

    public void processAudio(float[] samples) {
        if (mToneAFilter == null && !mConfiguration.isUnknownToneLoggingEnabled()) return;
        int offset = 0;
        while (offset < samples.length) {
            int remaining = mBlockSize - mSampleIndex;
            int toCopy = Math.min(remaining, samples.length - offset);
            System.arraycopy(samples, offset, mSampleBuffer, mSampleIndex, toCopy);
            mSampleIndex += toCopy;
            offset += toCopy;
            if (mSampleIndex >= mBlockSize) {
                analyzeBlock();
                mSampleIndex = 0;
            }
        }
    }

    private void analyzeBlock() {
        float totalEnergy = 0;
        for (int i = 0; i < mBlockSize; i++) {
            totalEnergy += mSampleBuffer[i] * mSampleBuffer[i];
        }
        totalEnergy /= mBlockSize;
        if (totalEnergy < 1e-10f) return;
        if (mToneAFilter != null) {
            float powerA = mToneAFilter.filter(mSampleBuffer);
            float normalizedPowerA = powerA / (totalEnergy * mBlockSize);
            if (normalizedPowerA > 0.05f) {
                mToneAConfirmations++;
            } else {
                mToneAConfirmations = 0;
            }
            if (mConfiguration.isLongTone()) {
                if (mToneAConfirmations * (mBlockSize * 1000 / mSampleRate) >= mConfiguration.getToneADurationMs()) {
                    notifyListeners();
                    mToneAConfirmations = 0;
                }
            } else if (mToneBFilter != null) {
                float powerB = mToneBFilter.filter(mSampleBuffer);
                float normalizedPowerB = powerB / (totalEnergy * mBlockSize);
                if (mToneAConfirmations * (mBlockSize * 1000 / mSampleRate) >= mConfiguration.getToneADurationMs()) {
                    mToneAActive = true;
                    mLastToneAMatchTime = System.currentTimeMillis();
                }
                if (normalizedPowerB > 0.05f) {
                    mToneBConfirmations++;
                    if (mToneAActive && System.currentTimeMillis() - mLastToneAMatchTime < 1000 &&
                        mToneBConfirmations * (mBlockSize * 1000 / mSampleRate) >= mConfiguration.getToneBDurationMs()) {
                        notifyListeners();
                        mToneAActive = false;
                        mToneBConfirmations = 0;
                    }
                } else {
                    mToneBConfirmations = 0;
                }
            }
        }
        if (mConfiguration.isUnknownToneLoggingEnabled() && mUnknownToneFilters != null) {
            for (int i = 0; i < mUnknownToneFilters.length; i++) {
                float power = mUnknownToneFilters[i].filter(mSampleBuffer);
                float normalizedPower = power / (totalEnergy * mBlockSize);
                if (normalizedPower > 0.10f) {
                    mUnknownToneConfirmations[i]++;
                    if (mUnknownToneConfirmations[i] == mUnknownToneThreshold) {
                        float durationMs = mUnknownToneThreshold * (mBlockSize * 1000.0f / mSampleRate);
                        mLog.info("Unknown Tone Detected - Frequency: {} Hz, Duration: >= {} ms", mUnknownToneFrequencies[i], durationMs);
                    }
                } else {
                    mUnknownToneConfirmations[i] = 0;
                }
            }
        }
    }
}
