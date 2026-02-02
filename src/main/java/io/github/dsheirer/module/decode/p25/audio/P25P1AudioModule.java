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
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import io.github.dsheirer.module.decode.p25.phase1.message.hdu.HDUMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU2Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.sample.Listener;
import java.util.ArrayList;
import java.util.List;

public class P25P1AudioModule extends ImbeAudioModule
{
    // Import the concealment strategy enum from config
    private static final DecodeConfigP25Phase1.AudioConcealmentStrategy DEFAULT_CONCEALMENT =
        DecodeConfigP25Phase1.AudioConcealmentStrategy.REPEAT_LAST;

    // Audio frame validation thresholds
    private static final float ENERGY_SPIKE_THRESHOLD = 8.0f;   // Max energy ratio between consecutive frames
    private static final float ENERGY_DROP_THRESHOLD = 0.05f;   // Min energy ratio (detect sudden drops)
    private static final int FRAME_SAMPLE_COUNT = 160;          // 20ms at 8kHz

    private boolean mEncryptedCall = false;
    private boolean mEncryptedCallStateEstablished = false;
    private boolean mIgnoreEncryptionState = false;
    private DecodeConfigP25Phase1.AudioConcealmentStrategy mConcealmentStrategy = DEFAULT_CONCEALMENT;

    // Frame validation state
    private float[] mLastGoodFrame = null;
    private float mLastFrameEnergy = 0.0f;
    private int mConcealedFrameCount = 0;
    private int mTotalFrameCount = 0;

    private SquelchStateListener mSquelchStateListener = new SquelchStateListener();
    private NonClippingGain mGain = new NonClippingGain(5.0f, 0.95f);
    private List<LDUMessage> mCachedLDUMessages = new ArrayList<>();

    public P25P1AudioModule(UserPreferences userPreferences, AliasList aliasList)
    {
        super(userPreferences, aliasList);
    }

    /**
     * Sets the audio frame concealment strategy.
     *
     * @param strategy the concealment strategy to use
     */
    public void setConcealmentStrategy(DecodeConfigP25Phase1.AudioConcealmentStrategy strategy)
    {
        mConcealmentStrategy = strategy != null ? strategy : DEFAULT_CONCEALMENT;
    }

    /**
     * Gets the current concealment strategy.
     *
     * @return the current strategy
     */
    public DecodeConfigP25Phase1.AudioConcealmentStrategy getConcealmentStrategy()
    {
        return mConcealmentStrategy;
    }

    /**
     * Gets the number of frames that were concealed since the last call start.
     *
     * @return concealed frame count
     */
    public int getConcealedFrameCount()
    {
        return mConcealedFrameCount;
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
                float[] audio = getAudioCodec().getAudio(frame);

                // Apply concealment if enabled and frame appears corrupted
                if(mConcealmentStrategy != DecodeConfigP25Phase1.AudioConcealmentStrategy.NONE && isFrameSuspicious(audio))
                {
                    mConcealedFrameCount++;
                    audio = getConcealmentAudio();
                }
                else
                {
                    // Track this as a good frame for future concealment
                    mLastGoodFrame = audio.clone();
                    mLastFrameEnergy = calculateFrameEnergy(audio);
                }

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
     * Calculates the RMS energy of an audio frame.
     *
     * @param audio the audio samples
     * @return RMS energy value
     */
    private float calculateFrameEnergy(float[] audio)
    {
        if(audio == null || audio.length == 0)
        {
            return 0.0f;
        }

        float sum = 0.0f;
        for(float sample : audio)
        {
            sum += sample * sample;
        }
        return (float)Math.sqrt(sum / audio.length);
    }

    /**
     * Determines if an audio frame appears suspicious based on energy analysis.
     * A frame is suspicious if:
     * - Energy spikes dramatically compared to the previous frame
     * - Energy drops to near-zero suddenly (not a natural fade)
     *
     * @param audio the decoded audio samples
     * @return true if the frame appears corrupted
     */
    private boolean isFrameSuspicious(float[] audio)
    {
        if(mLastGoodFrame == null)
        {
            // First frame - can't compare, assume good
            return false;
        }

        float currentEnergy = calculateFrameEnergy(audio);

        // Check for energy spike (corruption often causes loud noise)
        if(mLastFrameEnergy > 0.001f)  // Only check if previous frame had measurable energy
        {
            float energyRatio = currentEnergy / mLastFrameEnergy;

            if(energyRatio > ENERGY_SPIKE_THRESHOLD)
            {
                // Sudden energy spike - likely corruption
                return true;
            }

            // Check for sudden energy drop (different from natural fade)
            // Natural fades are gradual, corruption is sudden
            if(energyRatio < ENERGY_DROP_THRESHOLD && currentEnergy < 0.01f)
            {
                // Sudden drop to near-silence while previous frame had content
                return true;
            }
        }

        return false;
    }

    /**
     * Gets concealment audio based on the current strategy.
     *
     * @return audio samples for concealment
     */
    private float[] getConcealmentAudio()
    {
        if(mConcealmentStrategy == DecodeConfigP25Phase1.AudioConcealmentStrategy.REPEAT_LAST && mLastGoodFrame != null)
        {
            return mLastGoodFrame.clone();
        }
        // Fall through to silence for SILENCE strategy or if no good frame available
        return new float[FRAME_SAMPLE_COUNT];
    }

    /**
     * Resets frame validation state for a new call.
     */
    private void resetFrameValidation()
    {
        mLastGoodFrame = null;
        mLastFrameEnergy = 0.0f;
        mConcealedFrameCount = 0;
        mTotalFrameCount = 0;
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
