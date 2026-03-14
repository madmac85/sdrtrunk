/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
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
package io.github.dsheirer.module.decode.p25.audio;

import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.audio.codec.mbe.ImbeAudioModule;
import io.github.dsheirer.audio.squelch.SquelchState;
import io.github.dsheirer.audio.squelch.SquelchStateEvent;
import io.github.dsheirer.dsp.gain.NonClippingGain;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.hdu.HDUMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.IMBEFrameDiagnostic;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU2Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.sample.Listener;
import java.util.ArrayList;
import java.util.List;

public class P25P1AudioModule extends ImbeAudioModule
{
    private static final int FRAME_SAMPLE_COUNT = 160;          // 20ms at 8kHz

    private boolean mEncryptedCall = false;
    private boolean mEncryptedCallStateEstablished = false;
    private boolean mIgnoreEncryptionState = false;

    // Quality gate state
    private float[] mLastGoodFrame = null;
    private int mTotalFrameCount = 0;

    // Pre-codec quality gate: max IMBE FEC errors before substituting silence (0 = disabled)
    private int mMaxImbeErrors = 0;
    private int mPreCodecFilteredCount = 0;
    private int mConsecutiveGatedFrames = 0;
    private static final int GATE_FADE_START = 9;   // Start fading after 9 consecutive gated frames (1 LDU)
    private static final int GATE_FADE_LENGTH = 9;   // Fade to silence over next 9 frames
    private static final int CODEC_RESET_THRESHOLD = 5; // Reset codec after this many consecutive gated frames

    // Adaptive gate: auto-disable when pass rate is too low (non-bimodal error distribution)
    private static final int ADAPTIVE_WINDOW = 27;       // 3 LDUs worth of frames
    private static final float ADAPTIVE_DISABLE_RATE = 0.30f;  // Disable gate when <30% of frames pass
    private static final float ADAPTIVE_ENABLE_RATE = 0.50f;   // Re-enable gate when >50% pass
    private int mAdaptiveWindowIndex = 0;
    private int mAdaptivePassCount = 0;
    private boolean mAdaptiveGateDisabled = false;

    private SquelchStateListener mSquelchStateListener = new SquelchStateListener();
    private NonClippingGain mGain = new NonClippingGain(5.0f, 0.95f);
    private List<LDUMessage> mCachedLDUMessages = new ArrayList<>();

    public P25P1AudioModule(UserPreferences userPreferences, AliasList aliasList)
    {
        super(userPreferences, aliasList);
    }

    /**
     * Sets the maximum IMBE FEC errors allowed per frame before substituting silence.
     * When set to a value > 0, frames exceeding this threshold are replaced with silence
     * BEFORE being sent to the JMBE codec, preventing codec state contamination.
     * Set to 0 to disable pre-codec filtering (default).
     *
     * @param maxErrors maximum total FEC errors per IMBE frame (0 = disabled, recommended: 3)
     */
    public void setMaxImbeErrors(int maxErrors)
    {
        mMaxImbeErrors = Math.max(0, maxErrors);
    }

    public int getMaxImbeErrors()
    {
        return mMaxImbeErrors;
    }

    /**
     * Gets the total number of frames processed since the last call start.
     *
     * @return total frame count
     */
    public int getTotalFrameCount()
    {
        return mTotalFrameCount;
    }

    /**
     * Sets whether encryption detection should be ignored.
     * When true, all calls are assumed unencrypted and audio processing begins immediately
     * without waiting for encryption state verification from HDU or LDU2 messages.
     *
     * @param ignore true to bypass encryption detection
     */
    public void setIgnoreEncryptionState(boolean ignore)
    {
        mIgnoreEncryptionState = ignore;
        if(ignore)
        {
            // When ignoring encryption, treat state as established and unencrypted
            mEncryptedCallStateEstablished = true;
            mEncryptedCall = false;
        }
    }

    @Override
    protected int getTimeslot()
    {
        return 0;
    }

    @Override
    public Listener<SquelchStateEvent> getSquelchStateListener()
    {
        return mSquelchStateListener;
    }

    @Override
    public void reset()
    {
        getIdentifierCollection().clear();
    }

    @Override
    public void start()
    {
    }

    /**
     * Processes call header (HDU) and voice frame (LDU1/LDU2) messages to decode audio and to determine the
     * encrypted audio status of a call event. Only the HDU and LDU2 messages convey encrypted call status. If an
     * LDU1 message is received without a preceding HDU message, then the LDU1 message is cached until the first
     * LDU2 message is received and the encryption state can be determined. Both the LDU1 and the LDU2 message are
     * then processed for audio if the call is unencrypted.
     *
     * When ignoreEncryptionState is enabled, audio processing begins immediately without waiting for encryption
     * state verification, assuming all calls are unencrypted.
     */
    public void receive(IMessage message)
    {
        if(hasAudioCodec())
        {
            if(mEncryptedCallStateEstablished || mIgnoreEncryptionState)
            {
                if(message instanceof LDUMessage ldu)
                {
                    processAudio(ldu);
                }
            }
            else
            {
                if(message instanceof HDUMessage hdu && hdu.isValid())
                {
                    mEncryptedCallStateEstablished = true;
                    mEncryptedCall = hdu.getHeaderData().isEncryptedAudio();
                }
                else if(message instanceof LDU1Message ldu1)
                {
                    //When we receive an LDU1 message without first receiving the HDU message, cache the LDU1 Message
                    //until we can determine the encrypted call state from the next LDU2 message
                    mCachedLDUMessages.add(ldu1);
                }
                else if(message instanceof LDU2Message ldu2)
                {
                    if(ldu2.getEncryptionSyncParameters().isValid())
                    {
                        mEncryptedCallStateEstablished = true;
                        mEncryptedCall = ldu2.getEncryptionSyncParameters().isEncryptedAudio();
                    }

                    if(mEncryptedCallStateEstablished)
                    {
                        for(LDUMessage cachedLdu : mCachedLDUMessages)
                        {
                            processAudio(cachedLdu);
                        }

                        mCachedLDUMessages.clear();
                        processAudio(ldu2);
                    }
                    else
                    {
                        mCachedLDUMessages.add(ldu2);
                    }
                }
            }
        }
    }

    /**
     * Processes an audio packet by decoding the IMBE audio frames and rebroadcasting them as PCM audio packets.
     * Applies frame validation and concealment when corrupted frames are detected.
     */
    private void processAudio(LDUMessage ldu)
    {
        if(!mEncryptedCall)
        {
            for(byte[] frame : ldu.getIMBEFrames())
            {
                mTotalFrameCount++;

                // Pre-codec quality gate: check IMBE FEC errors before codec decode
                if(mMaxImbeErrors > 0)
                {
                    IMBEFrameDiagnostic.FrameErrors fe = IMBEFrameDiagnostic.analyzeFrame(frame);
                    boolean exceedsThreshold = fe.totalErrors() > mMaxImbeErrors;

                    // Adaptive gate: track pass rate and auto-disable when most frames fail
                    updateAdaptiveGate(exceedsThreshold);

                    if(exceedsThreshold && !mAdaptiveGateDisabled)
                    {
                        mPreCodecFilteredCount++;
                        mConsecutiveGatedFrames++;

                        // Reset codec after sustained corruption to prevent state contamination
                        if(mConsecutiveGatedFrames == CODEC_RESET_THRESHOLD)
                        {
                            getAudioCodec().reset();
                        }

                        // Repeat last good frame with fade-out, or silence if none available
                        if(mLastGoodFrame != null && mConsecutiveGatedFrames <= GATE_FADE_START + GATE_FADE_LENGTH)
                        {
                            float[] repeated = mLastGoodFrame.clone();
                            if(mConsecutiveGatedFrames > GATE_FADE_START)
                            {
                                float fade = 1.0f - (float)(mConsecutiveGatedFrames - GATE_FADE_START) / GATE_FADE_LENGTH;
                                for(int i = 0; i < repeated.length; i++)
                                {
                                    repeated[i] *= fade;
                                }
                            }
                            addAudio(mGain.apply(repeated));
                        }
                        else
                        {
                            addAudio(mGain.apply(new float[FRAME_SAMPLE_COUNT]));
                        }
                        continue;
                    }
                }

                // Good frame passed the gate (or gate adaptively disabled) — reset consecutive counter
                mConsecutiveGatedFrames = 0;

                float[] audio = getAudioCodec().getAudio(frame);
                mLastGoodFrame = audio.clone();
                audio = mGain.apply(audio);
                addAudio(audio);
            }
        }
        else
        {
            //Encrypted audio processing not implemented
        }
    }

    /**
     * Updates the adaptive gate state based on whether the current frame exceeds the threshold.
     * When most frames fail the gate (non-bimodal error distribution), the gate auto-disables
     * to let the codec attempt partial decode instead of producing silence/repetition.
     */
    private void updateAdaptiveGate(boolean exceedsThreshold)
    {
        mAdaptiveWindowIndex++;
        if(!exceedsThreshold)
        {
            mAdaptivePassCount++;
        }

        if(mAdaptiveWindowIndex >= ADAPTIVE_WINDOW)
        {
            float passRate = (float)mAdaptivePassCount / mAdaptiveWindowIndex;

            if(mAdaptiveGateDisabled)
            {
                // Re-enable if conditions improved
                if(passRate >= ADAPTIVE_ENABLE_RATE)
                {
                    mAdaptiveGateDisabled = false;
                }
            }
            else
            {
                // Disable if too few frames pass
                if(passRate < ADAPTIVE_DISABLE_RATE)
                {
                    mAdaptiveGateDisabled = true;
                }
            }

            // Reset window
            mAdaptiveWindowIndex = 0;
            mAdaptivePassCount = 0;
        }
    }

    /**
     * Resets frame validation state for a new call.
     */
    private void resetFrameValidation()
    {
        mLastGoodFrame = null;
        mPreCodecFilteredCount = 0;
        mConsecutiveGatedFrames = 0;
        mTotalFrameCount = 0;
        mAdaptiveWindowIndex = 0;
        mAdaptivePassCount = 0;
        mAdaptiveGateDisabled = false;
    }

    /**
     * Wrapper for squelch state to process end of call actions.  At call end the encrypted call state established
     * flag is reset so that the encrypted audio state for the next call can be properly detected and we send an
     * END audio packet so that downstream processors like the audio recorder can properly close out a call sequence.
     *
     * Note: When ignoreEncryptionState is enabled, the encryption state flags remain set to unencrypted.
     */
    public class SquelchStateListener implements Listener<SquelchStateEvent>
    {
        @Override
        public void receive(SquelchStateEvent event)
        {
            if(event.getSquelchState() == SquelchState.SQUELCH)
            {
                closeAudioSegment();
                // Only reset encryption state if we're not ignoring it
                if(!mIgnoreEncryptionState)
                {
                    mEncryptedCallStateEstablished = false;
                    mEncryptedCall = false;
                }
                mCachedLDUMessages.clear();
                resetFrameValidation();
            }
        }
    }
}
