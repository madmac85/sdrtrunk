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

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.bits.IntField;
import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.edac.bch.BCH_63_16_23_P25;
import io.github.dsheirer.message.DroppedSamplesMessage;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25MessageFactory;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.pdu.PDUHeader;
import io.github.dsheirer.module.decode.p25.phase1.message.pdu.PDUMessageFactory;
import io.github.dsheirer.module.decode.p25.phase1.message.pdu.PDUSequence;
import io.github.dsheirer.module.decode.p25.phase1.message.tsbk.TSBKMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.tsbk.TSBKMessageFactory;
import io.github.dsheirer.module.decode.p25.phase1.sync.P25P1HardSyncDetector;
import io.github.dsheirer.module.decode.p25.phase1.sync.P25P1SoftSyncDetector;
import io.github.dsheirer.module.decode.p25.phase1.sync.P25P1SoftSyncDetectorFactory;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.sample.Listener;

/**
 * Provides message framing for the demodulated dibit stream.  This framer is notified by an external sync detection
 * process using the two syncDetected() methods below to indicate if the NID that follows the sync was correctly error
 * detected and corrected.  When the NID does not pass error correction, we use a PLACEHOLDER data unit ID to allow the
 * uncertain message to assemble and then we'll inspect before and after data unit IDs and the quantity of captured
 * dibits to make a best guess on what the assembled message represents.
 */
public class P25P1MessageFramer
{
    private static final int DIBIT_LENGTH_NID = 33; //32 dibits (64 bits) +1 status
    private static final float SYNC_DETECTION_THRESHOLD = 60;
    private static final float SYNC_FALLBACK_THRESHOLD = 52;     // Strategy 1: Conservative fallback for weak signals
    private static final float SYNC_FADE_THRESHOLD = 48;          // Strategy 3: Lower threshold during fade
    private static final float SYNC_INITIAL_THRESHOLD = 38;       // Strategy 4: Initial acquisition for weak preambles
    private static final float SYNC_ULTRA_INITIAL_THRESHOLD = 34; // Strategy 4: Ultra-low for very first 50ms
    private static final int RECOVERY_WINDOW_SYMBOLS = 240;       // Strategy 2: 50ms recovery window (first HDU sync)
    private static final int INITIAL_ACQUISITION_WINDOW_SYMBOLS = 960;  // Strategy 4: 200ms at 4800 symbols/sec
    private final BCH_63_16_23_P25 mBCHDecoder = new BCH_63_16_23_P25();
    private int mMaxBchErrors = 11; // Default: full T=11 capability (no filtering)
    private static final IntField NAC_FIELD = IntField.length12(0);
    private static final IntField DUID_FIELD = IntField.length4(12);
    private final NACTracker mNACTracker = new NACTracker();
    private Dibit[] mNIDBuffer = new Dibit[DIBIT_LENGTH_NID];
    private int mNIDPointer = 0;
    private final P25P1SoftSyncDetector mSoftSyncDetector = P25P1SoftSyncDetectorFactory.getDetector();
    private final P25P1HardSyncDetector mHardSyncDetector = new P25P1HardSyncDetector();
    private boolean mSyncDetected = false;

    private static final double MILLISECONDS_PER_SYMBOL = 1.0 / 4800.0 / 1000.0;
    private Listener<IMessage> mMessageListener;
    private boolean mMessageAssemblyRequired = false;
    private boolean mRunning = false;
    private int mDibitCounter = 58; //Set to 1-greater than SYNC+NID to avoid triggering message assembly on startup
    private int mDibitSinceTimestampCounter = 0;
    private int mStatusSymbolDibitCounter = 36; //Set to 1-greater than the suppression trigger at 35 dibits
    private long mReferenceTimestamp = 0;
    private P25P1MessageAssembler mMessageAssembler;
    //Package-private for testing: when true, blocks sync detections during active assembly (upstream audio squeak fix).
    //When false, accepts all syncs unconditionally (pre-merge behavior). Default true preserves upstream behavior.
    boolean mSyncGuardEnabled = true;
    private P25P1DataUnitID mPreviousDataUnitID = P25P1DataUnitID.PLACE_HOLDER;
    private P25P1DataUnitID mDetectedDataUnitID = P25P1DataUnitID.PLACE_HOLDER;
    private int mDetectedNAC = 0;
    private int mDetectedSyncBitErrors = 0;
    private final P25P1ChannelStatusProcessor mChannelStatusProcessor = new P25P1ChannelStatusProcessor();
    private PDUSequence mPDUSequence;
    private int mDebugSymbolCount = 0;

    // Diagnostic counters for v2 analysis
    private int mSyncDetectionCount = 0;
    private int mNIDDecodeSuccessCount = 0;
    private int mNIDDecodeFailCount = 0;
    int mSyncBlockedCount = 0; //Package-private for testing: count of syncs blocked by the guard

    // Strategy 1: Adaptive sync threshold based on signal energy
    private ISignalEnergyProvider mEnergyProvider;
    private int mFallbackSyncCount = 0;

    // Strategy 2: Boundary recovery window - use hard sync after transmission boundary
    private boolean mBoundaryRecoveryActive = false;
    private int mRecoverySymbolCount = 0;
    private int mRecoverySyncCount = 0;

    // Strategy 3: Fade recovery - lower threshold when signal is fading
    private boolean mFadeRecoveryActive = false;
    private int mFadeRecoverySyncCount = 0;

    // Strategy 4: Initial acquisition - lower threshold for weak preambles
    private boolean mInitialAcquisitionActive = false;
    private int mAcquisitionWindowSymbolCount = 0;
    private int mInitialAcquisitionSyncCount = 0;

    // Strategy 5: Sync flywheel — predict next DUID when sync is lost but frame timing is known
    private static final int MAX_FLYWHEEL_MISSES = 3;
    private boolean mFlywheelActive = false;
    private boolean mFlywheelAssembly = false; // true when current assembler is from flywheel prediction
    private int mFlywheelConsecutiveMisses = 0;
    private int mFlywheelAttemptCount = 0;
    private int mFlywheelSuccessCount = 0;
    private int mFlywheelMissCount = 0;

    /**
     * Constructs an instance
     */
    public P25P1MessageFramer()
    {
    }

    /**
     * Process soft symbol and apply soft symbol sync pattern detection.
     * Uses adaptive sync thresholds based on signal energy and recovery state.
     *
     * @param softSymbol demodulated soft symbol
     * @param symbol as decision from the soft symbol
     * @return true if a sync pattern is detected and the following NID is decoded correctly.
     */
    public boolean processWithSoftSyncDetect(float softSymbol, Dibit symbol)
    {
        // Track recovery window timeout (Strategy 2)
        if(mBoundaryRecoveryActive)
        {
            mRecoverySymbolCount++;
            if(mRecoverySymbolCount >= RECOVERY_WINDOW_SYMBOLS)
            {
                mBoundaryRecoveryActive = false;
            }
        }

        // Track initial acquisition window timeout (Strategy 4)
        if(mInitialAcquisitionActive)
        {
            mAcquisitionWindowSymbolCount++;
            if(mAcquisitionWindowSymbolCount >= INITIAL_ACQUISITION_WINDOW_SYMBOLS)
            {
                mInitialAcquisitionActive = false;
            }
        }

        boolean validNIDDetected = process(symbol);

        float syncScore = mSoftSyncDetector.process(softSymbol);

        // Primary threshold - standard sync detection
        if(syncScore > SYNC_DETECTION_THRESHOLD)
        {
            syncDetected();
        }
        // Strategy 4: Initial acquisition with adaptive threshold for weak preambles
        else if(mInitialAcquisitionActive && syncScore > getCurrentInitialThreshold())
        {
            syncDetected();
            mInitialAcquisitionSyncCount++;
        }
        // Strategy 3: Fade recovery - even lower threshold during signal fade
        else if(mFadeRecoveryActive && syncScore > SYNC_FADE_THRESHOLD)
        {
            syncDetected();
            mFadeRecoverySyncCount++;
            mFadeRecoveryActive = false; // One-shot
        }
        // Strategy 1: Fallback threshold when signal energy confirms transmission present
        else if(syncScore > SYNC_FALLBACK_THRESHOLD &&
                mEnergyProvider != null &&
                mEnergyProvider.isSignalPresent())
        {
            syncDetected();
            mFallbackSyncCount++;
        }
        // Strategy 2: Hard sync during boundary recovery (more bit-error tolerant)
        else if(mBoundaryRecoveryActive && mHardSyncDetector.process(symbol))
        {
            syncDetected();
            mRecoverySyncCount++;
        }

        return validNIDDetected;
    }

    /**
     * Gets the current threshold for initial acquisition mode with adaptive ramping.
     * Ramps from 34 (ultra-initial) to 38 to 48 to 52 over 200ms in 4 phases.
     * This aggressive ramping gives weak preamble transmissions more opportunity to sync.
     *
     * @return the current threshold based on acquisition window progress
     */
    private float getCurrentInitialThreshold()
    {
        if(!mInitialAcquisitionActive)
        {
            return SYNC_FALLBACK_THRESHOLD;
        }

        // Calculate progress through acquisition window (0 to 1)
        float progress = (float)mAcquisitionWindowSymbolCount / INITIAL_ACQUISITION_WINDOW_SYMBOLS;

        if(progress < 0.25f)
        {
            // First quarter (0-50ms): ultra-low threshold (34) for very weak starts
            return SYNC_ULTRA_INITIAL_THRESHOLD;
        }
        else if(progress < 0.50f)
        {
            // Second quarter (50-100ms): initial threshold (38)
            return SYNC_INITIAL_THRESHOLD;
        }
        else if(progress < 0.75f)
        {
            // Third quarter (100-150ms): fade threshold (48)
            return SYNC_FADE_THRESHOLD;
        }
        else
        {
            // Final quarter (150-200ms): fallback threshold (52)
            return SYNC_FALLBACK_THRESHOLD;
        }
    }

    /**
     * Process symbol decision and perform hard symbol sync detection.
     * @param symbol decision
     */
    public boolean processWithHardSyncDetect(Dibit symbol)
    {
        boolean validNIDDetected = process(symbol);

        if(mHardSyncDetector.process(symbol))
        {
            syncDetected();
        }

        return validNIDDetected;
    }

    /**
     * Externally triggered sync detection.
     *
     * The sync guard (de0e722f) blocks false syncs that arrive mid-assembly to prevent audio squeaks caused by
     * premature force-completion of LDU assemblers.  However, the guard must allow syncs during PLACEHOLDER assembly
     * to enable recovery from sample drops.  Without this exception, dropped samples cause a cascading failure:
     * corrupted assembler → placeholder assembler → blocked syncs → new placeholder → blocked syncs, with recovery
     * only possible in a ~12ms window between placeholders (~3% chance per message boundary).  This can lock out
     * sync recovery for 10+ seconds, corrupting an entire call's audio.
     *
     * The fix: allow sync detection when the current assembler is a speculative PLACEHOLDER (NID decode failed).
     * Real message assembly (LDU, HDU, TDU, etc.) remains protected by the guard.
     */
    public void syncDetected()
    {
        //When the sync guard is disabled, accept all sync detections unconditionally (pre-merge behavior).
        if(!mSyncGuardEnabled)
        {
            mSyncDetected = true;
            mNIDPointer = 0;
            mSyncDetectionCount++;
            return;
        }

        //Allow sync detection when no assembler is active, when the assembler is a speculative placeholder,
        //or when the assembler is a flywheel prediction (so real syncs can interrupt flywheel).
        //Placeholders are created when NID decode fails — they collect data speculatively but are discarded on
        //dispatch.  Blocking syncs during placeholder assembly prevents recovery from sample drops/discontinuities
        //that corrupt the demodulator state.  Real message assembly (known DUIDs) remains protected.
        if(mMessageAssembler == null ||
           mMessageAssembler.getDataUnitID() == P25P1DataUnitID.PLACE_HOLDER ||
           mFlywheelAssembly)
        {
            mSyncDetected = true;
            mNIDPointer = 0;
            mSyncDetectionCount++;
        }
        else
        {
            mSyncBlockedCount++;
        }
    }

    /**
     * Process hard symbol decision without sync detection.
     * @param symbol that was demodulated.
     */
    public boolean process(Dibit symbol)
    {
        boolean validNIDDetected = false;

        mDebugSymbolCount++;
        mDibitSinceTimestampCounter++;

        //Strip status symbol after every 35 dibits/70 bits.  This counter is reset to zero on sync detect and runs
        //continuously even when we don't have a sync detect and not assembling a message.
        mStatusSymbolDibitCounter++;

        if(mSyncDetected)
        {
            mNIDBuffer[mNIDPointer++] = symbol;

            if(mNIDPointer >= DIBIT_LENGTH_NID)
            {
                validNIDDetected = checkNID();
                mSyncDetected = false;
            }
        }

        if(mStatusSymbolDibitCounter == 36)
        {
            if(mMessageAssemblyRequired || mMessageAssembler != null)
            {
                //Send status dibit to channel status processor to identify ISP or OSP channel
                mChannelStatusProcessor.receive(symbol);
            }

            mStatusSymbolDibitCounter = 0;
            mDibitCounter++;
            return false;
        }

        if(mMessageAssembler != null)
        {
            //Important sequencing - delay checking message complete until the next non-status dibit arrives
            //so that we can fully consume any trailing status dibit first, before dispatching the message.
            if(mMessageAssembler.isComplete())
            {
                dispatchMessage();

                //If we still have an assembler, feed it the current dibit (e.g. TSBK and PDU continuation block assembly)
                if(mMessageAssembler != null)
                {
                    mMessageAssembler.receive(symbol);
                }
            }
            else
            {
                mMessageAssembler.receive(symbol);
            }
        }
        //Start a message assembler after ignoring 24x Sync, 32x NID, and 1x status dibits. Trigger assembler
        // construction at dibit count 57 and feed the current dibit to the assembler.
        else if(mDibitCounter == 57)
        {
            if(mMessageAssemblyRequired)
            {
                mMessageAssembler = new P25P1MessageAssembler(mDetectedNAC, mDetectedDataUnitID);
                mMessageAssemblyRequired = false;
                mFlywheelAssembly = false;
            }
            //Strategy 5: Flywheel — predict DUID when sync is lost but frame timing is known
            else if(mFlywheelActive && mFlywheelConsecutiveMisses < MAX_FLYWHEEL_MISSES && mDetectedNAC > 0)
            {
                P25P1DataUnitID predicted = predictNextDUID(mPreviousDataUnitID);

                if(predicted != null)
                {
                    mDetectedDataUnitID = predicted;
                    mMessageAssembler = new P25P1MessageAssembler(mDetectedNAC, predicted);
                    mFlywheelAssembly = true;
                    mFlywheelAttemptCount++;
                    mFlywheelConsecutiveMisses++;
                    mFlywheelMissCount++;
                }
                else
                {
                    //Can't predict — fall through to placeholder
                    mDetectedDataUnitID = P25P1DataUnitID.PLACE_HOLDER;
                    mMessageAssembler = new P25P1MessageAssembler(mDetectedNAC, mDetectedDataUnitID);
                    mFlywheelAssembly = false;
                    mFlywheelActive = false;
                }
            }
            else if(mDetectedNAC > 0)
            {
                //Start a placeholder message assembly.  If it completes before another sync detect, throw it away
                mDetectedDataUnitID = P25P1DataUnitID.PLACE_HOLDER;
                mMessageAssembler = new P25P1MessageAssembler(mDetectedNAC, mDetectedDataUnitID);
                mFlywheelAssembly = false;
            }
        }
        else if(mDibitCounter >= 4800) //4800x (1-sec).
        {
            mDibitCounter -= 4800;
            broadcast(new SyncLossMessage(getTimestamp(), 9600, Protocol.APCO25));
        }

        mDibitCounter++;

        return validNIDDetected;
    }

    /**
     * Indicates if there is a non-null message assembler and it is completed, but not yet dispatched.
     * @return true if there is a complete message.
     */
    public boolean isComplete()
    {
        return mMessageAssembler != null && mMessageAssembler.isComplete() && mStatusSymbolDibitCounter == 35;
    }

    /**
     * Dispatch the message currently in the message assembler.
     */
    private void dispatchMessage()
    {
        //Note: the message assembler should have a valid DUID on it via the forceCompletion() method.  Capture the
        //current DUID as the previous, before the assembler is nullified.
        mPreviousDataUnitID = mMessageAssembler.getDataUnitID();

        if(mMessageListener != null)
        {
            switch(mMessageAssembler.getDataUnitID())
            {
                case TRUNKING_SIGNALING_BLOCK_1:
                case TRUNKING_SIGNALING_BLOCK_2:
                case TRUNKING_SIGNALING_BLOCK_3:
                    dispatchTSBK();
                    break;
                case PACKET_DATA_UNIT:
                case PACKET_DATA_UNIT_BLOCK_1:
                case PACKET_DATA_UNIT_BLOCK_2:
                case PACKET_DATA_UNIT_BLOCK_3:
                case PACKET_DATA_UNIT_BLOCK_4:
                case PACKET_DATA_UNIT_BLOCK_5:
                    dispatchPDU();
                    break;
                case TERMINATOR_DATA_UNIT:
                    dispatchTDU();
                    break;
                case TERMINATOR_DATA_UNIT_LINK_CONTROL:
                    dispatchTDULC();
                    break;
                case PLACE_HOLDER:
                    mMessageAssembler = null;
                    break;
                default:
                    dispatchOther();
                    break;
            }
        }
        else
        {
            mMessageAssembler = null;
        }
    }

    /**
     * Updates the dibit counter with the dibits collected on the current message before message assembler disposal.
     */
    private void adjustDibitCounterFromMessageAssembler()
    {
        if(mMessageAssembler != null)
        {
            mDibitCounter -= mMessageAssembler.getDataUnitID().getElapsedDibitLength(); //SYNC + NID + Message
        }
    }

    private void dispatchTDU()
    {
        adjustDibitCounterFromMessageAssembler();

        CorrectedBinaryMessage cbm = mMessageAssembler.getMessage();
        P25P1Message message = P25MessageFactory.create(mMessageAssembler.getDataUnitID(), mMessageAssembler.getNAC(),
                getTimestamp(), cbm);

        if(message != null)
        {
            message.getMessage().incrementCorrectedBitCount(mDetectedSyncBitErrors);
            broadcast(message);
        }
        else
        {
            broadcast(new SyncLossMessage(getTimestamp(), cbm.currentSize(), Protocol.APCO25));
        }

        mMessageAssembler = null;
    }

    private void dispatchTDULC()
    {
        CorrectedBinaryMessage cbm = mMessageAssembler.getMessage();
        P25P1Message message = P25MessageFactory.create(mMessageAssembler.getDataUnitID(), mMessageAssembler.getNAC(),
                getTimestamp(), cbm);

        if(message != null)
        {
            broadcast(message);
        }
        else
        {
            broadcast(new SyncLossMessage(getTimestamp(), cbm.currentSize(), Protocol.APCO25));
        }

        adjustDibitCounterFromMessageAssembler();
        mMessageAssembler = null;
    }

    /**
     * Dispatches the message currently in the message assembler when the DUID is not PDU or TSBK.
     */
    private void dispatchOther()
    {
        adjustDibitCounterFromMessageAssembler();

        CorrectedBinaryMessage cbm = mMessageAssembler.getMessage();
        P25P1Message message = P25MessageFactory.create(mMessageAssembler.getDataUnitID(), mMessageAssembler.getNAC(),
                getTimestamp(), cbm);

        if(message != null)
        {
            broadcast(message);
        }
        else
        {
            broadcast(new SyncLossMessage(getTimestamp(), cbm.currentSize(), Protocol.APCO25));
        }

        mMessageAssembler = null;
    }

    /**
     * Indicates if a message is being or about to be assembled and that message is not yet complete.
     */
    public boolean isAssembling()
    {
        return (mMessageAssembler != null && !mMessageAssembler.isComplete()) || mMessageAssemblyRequired || mDibitCounter < 2;
    }

    public P25P1DataUnitID getAssemblingDUID()
    {
        if(isAssembling())
        {
            return mMessageAssembler.getDataUnitID();
        }

        return null;
    }

    /**
     * Dispatches the message currently in the message assembler when the DUID is TSBK1, TSBK2, or TSBK3.
     */
    private void dispatchTSBK()
    {
        switch(mMessageAssembler.getDataUnitID())
        {
            case TRUNKING_SIGNALING_BLOCK_1:
                CorrectedBinaryMessage message1 = mMessageAssembler.getMessage().getSubMessage(0, 196);
                TSBKMessage tsbk1 = TSBKMessageFactory.create(mChannelStatusProcessor.getDirection(), mMessageAssembler.getDataUnitID(), message1, mMessageAssembler.getNAC(), getTimestamp());

                if(tsbk1 != null)
                {
                    //Add in the sync and nid detected bit error counts
                    tsbk1.getMessage().incrementCorrectedBitCount(mDetectedSyncBitErrors);
                    broadcast(tsbk1);

                    if(mMessageAssembler.getMessage().currentSize() >= 391) //Detect forced completion
                    {
                        mMessageAssembler.setDataUnitID(P25P1DataUnitID.TRUNKING_SIGNALING_BLOCK_2);
                        dispatchTSBK(); //Recursive call
                    }
                    else if(tsbk1.isValid() && tsbk1.isLastBlock())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        mMessageAssembler = null;
                    }
                    else //Reconfigure the assembler to continue capturing TSBK2
                    {
                        mMessageAssembler.reconfigure(P25P1DataUnitID.TRUNKING_SIGNALING_BLOCK_2);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case TRUNKING_SIGNALING_BLOCK_2:
                CorrectedBinaryMessage message2 = mMessageAssembler.getMessage().getSubMessage(196, 392);
                TSBKMessage tsbk2 = TSBKMessageFactory.create(mChannelStatusProcessor.getDirection(), mMessageAssembler.getDataUnitID(), message2, mMessageAssembler.getNAC(), getTimestamp());

                if(tsbk2 != null)
                {
                    broadcast(tsbk2);

                    if(mMessageAssembler.getMessage().currentSize() >= 588) //Detect forced completion
                    {
                        mMessageAssembler.setDataUnitID(P25P1DataUnitID.TRUNKING_SIGNALING_BLOCK_3);
                        dispatchTSBK(); //Recursive call
                    }
                    else if(tsbk2.isValid() && tsbk2.isLastBlock())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        mMessageAssembler = null;
                    }
                    else //Reconfigure the assembler to continue capturing TSBK3
                    {
                        mMessageAssembler.reconfigure(P25P1DataUnitID.TRUNKING_SIGNALING_BLOCK_3);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case TRUNKING_SIGNALING_BLOCK_3:
                CorrectedBinaryMessage message3 = mMessageAssembler.getMessage().getSubMessage(392, 588);
                TSBKMessage tsbk3 = TSBKMessageFactory.create(mChannelStatusProcessor.getDirection(), mMessageAssembler.getDataUnitID(), message3, mMessageAssembler.getNAC(), getTimestamp());
                broadcast(tsbk3);

                adjustDibitCounterFromMessageAssembler();
                mMessageAssembler = null;
                break;
            default:
                System.out.println("Unexpected TSBK DUID: " +  mMessageAssembler.getDataUnitID());
        }
    }

    /**
     * Dispatches a sync loss message to account for lost bits.
     * @param bitCount that was lost.
     */
    private void dispatchSyncLoss(int bitCount)
    {
        if(bitCount > 0)
        {
            broadcast(new SyncLossMessage(getTimestamp(), bitCount, Protocol.APCO25));
        }
    }

    /**
     * Dispatches a dropped samples/symbols message to account for (potentially) dropped samples when detected.
     * @param bitCount representing the quantity of symbols that are missing
     */
    private void dispatchDroppedSamples(int bitCount)
    {
        if(bitCount > 0)
        {
            broadcast(new DroppedSamplesMessage(getTimestamp(), bitCount, Protocol.APCO25));
        }
    }


    /**
     * Dispatches the message currently in the message assembler when the DUID is PDU or PDU1
     */
    private void dispatchPDU()
    {
        switch(mMessageAssembler.getDataUnitID())
        {
            case PACKET_DATA_UNIT:
                CorrectedBinaryMessage message = mMessageAssembler.getMessage().getSubMessage(0, 196);
                PDUHeader header = PDUMessageFactory.createHeader(message);

                if(header != null)
                {
                    mPDUSequence = new PDUSequence(header, getTimestamp(), mMessageAssembler.getNAC());

                    if(mPDUSequence.getHeader().isValid() && mPDUSequence.getHeader().getBlocksToFollowCount() > 0)
                    {
                        //Setup to catch the sequence of data blocks that follow the header
                        mMessageAssembler.reconfigure(P25P1DataUnitID.PACKET_DATA_UNIT_BLOCK_1);
                    }
                    else
                    {
                        adjustDibitCounterFromMessageAssembler();
                        dispatchPDUSequence();
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                    mPDUSequence = null;
                }
                break;
            case PACKET_DATA_UNIT_BLOCK_1:
                if(mPDUSequence != null)
                {
                    CorrectedBinaryMessage messageB1 = mMessageAssembler.getMessage().getSubMessage(196, 392);

                    if(mPDUSequence.getHeader().isConfirmationRequired())
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createConfirmedDataBlock(messageB1));
                    }
                    else
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createUnconfirmedDataBlock(messageB1));
                    }

                    if(mPDUSequence.isComplete())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        dispatchPDUSequence();
                    }
                    else
                    {
                        //Setup to catch the next data block
                        mMessageAssembler.reconfigure(P25P1DataUnitID.PACKET_DATA_UNIT_BLOCK_2);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case PACKET_DATA_UNIT_BLOCK_2:
                if(mPDUSequence != null)
                {
                    CorrectedBinaryMessage messageB2 = mMessageAssembler.getMessage().getSubMessage(392, 588);

                    if(mPDUSequence.getHeader().isConfirmationRequired())
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createConfirmedDataBlock(messageB2));
                    }
                    else
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createUnconfirmedDataBlock(messageB2));
                    }

                    if(mPDUSequence.isComplete())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        dispatchPDUSequence();
                    }
                    else
                    {
                        //Setup to catch the next data block
                        mMessageAssembler.reconfigure(P25P1DataUnitID.PACKET_DATA_UNIT_BLOCK_3);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case PACKET_DATA_UNIT_BLOCK_3:
                if(mPDUSequence != null)
                {
                    CorrectedBinaryMessage messageB3 = mMessageAssembler.getMessage().getSubMessage(588, 784);

                    if(mPDUSequence.getHeader().isConfirmationRequired())
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createConfirmedDataBlock(messageB3));
                    }
                    else
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createUnconfirmedDataBlock(messageB3));
                    }

                    if(mPDUSequence.isComplete())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        dispatchPDUSequence();
                    }
                    else
                    {
                        //Setup to catch the next data block
                        mMessageAssembler.reconfigure(P25P1DataUnitID.PACKET_DATA_UNIT_BLOCK_4);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case PACKET_DATA_UNIT_BLOCK_4:
                if(mPDUSequence != null)
                {
                    CorrectedBinaryMessage messageB4 = mMessageAssembler.getMessage().getSubMessage(784, 980);

                    if(mPDUSequence.getHeader().isConfirmationRequired())
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createConfirmedDataBlock(messageB4));
                    }
                    else
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createUnconfirmedDataBlock(messageB4));
                    }

                    if(mPDUSequence.isComplete())
                    {
                        adjustDibitCounterFromMessageAssembler();
                        dispatchPDUSequence();
                    }
                    else
                    {
                        //Setup to catch the last data block
                        mMessageAssembler.reconfigure(P25P1DataUnitID.PACKET_DATA_UNIT_BLOCK_5);
                    }
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            case PACKET_DATA_UNIT_BLOCK_5:
                if(mPDUSequence != null)
                {
                    CorrectedBinaryMessage messageB5 = mMessageAssembler.getMessage().getSubMessage(980, 1176);

                    if(mPDUSequence.getHeader().isConfirmationRequired())
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createConfirmedDataBlock(messageB5));
                    }
                    else
                    {
                        mPDUSequence.addDataBlock(PDUMessageFactory.createUnconfirmedDataBlock(messageB5));
                    }

                    adjustDibitCounterFromMessageAssembler();
                    dispatchPDUSequence();
                }
                else
                {
                    adjustDibitCounterFromMessageAssembler();
                    mMessageAssembler = null;
                }
                break;
            default:
                System.out.println("Unexpected PDU DUID: " + mMessageAssembler.getMessage());
                mMessageAssembler = null;
                mPDUSequence = null;
        }
    }

    /**
     * Dispatches a completed PDU sequence
     */
    private void dispatchPDUSequence()
    {
        P25P1Message pduMessage = PDUMessageFactory.create(mPDUSequence, mMessageAssembler.getNAC(), getTimestamp());
        if(pduMessage != null)
        {
            if(pduMessage.getMessage() != null)
            {
                pduMessage.getMessage().incrementCorrectedBitCount(mDetectedSyncBitErrors);
            }

            broadcast(pduMessage);
        }
        mMessageAssembler = null;
        mPDUSequence = null;
    }

    private void reset()
    {
        mPDUSequence = null;
        mStatusSymbolDibitCounter = 0;
    }

    /**
     * Broadcasts the assembled message to the registered listener.
     * @param message to broadcast - ignored if there is no registered listener.
     */
    private void broadcast(IMessage message)
    {
//        System.out.println("Symbols: " + mDebugSymbolCount);
        if(mRunning && message != null && mMessageListener != null)
        {
            mMessageListener.receive(message);
        }
    }

    /**
     * Externally provided trigger that a sync pattern is detected and the next arriving dibit is the first symbol of
     * that detected sync.  This method is triggered when sync is detected and either:
     * a) a valid NID is decoded from the look-ahead sample buffer or,
     * b) the sync optimization process produces a high-quality correlation score
     *
     * When the trigger is option b, the DUID will be the PLACEHOLDER.
     *
     * @param nac value decoded from the NID.
     * @param dataUnitID decoded from the NID
     * @param detectedBitErrors across the SYNc and NID
     */
    private int mDuidCorrectionCount = 0;

    public int getDuidCorrectionCount() { return mDuidCorrectionCount; }

    public void nidDetected(int nac, P25P1DataUnitID dataUnitID, int detectedBitErrors)
    {
        mDetectedDataUnitID = dataUnitID;

        //If the DUID is UNKNOWN, use the PLACEHOLDER and don't overwrite the previously detected NAC
        if(mDetectedDataUnitID == P25P1DataUnitID.UNKNOWN)
        {
            mDetectedDataUnitID = P25P1DataUnitID.PLACE_HOLDER;
        }

        //Context-aware DUID correction: BCH error correction can produce a wrong DUID (commonly TDU
        //when the actual frame is LDU). When the previous DUID predicts an LDU continuation but BCH
        //decoded a TDU, override with the predicted DUID. This fixes the "TDU flood" problem where
        //voice frames are misidentified as terminators during active calls.
        if(mDetectedDataUnitID == P25P1DataUnitID.TERMINATOR_DATA_UNIT)
        {
            P25P1DataUnitID predicted = predictNextDUID(mPreviousDataUnitID);
            if(predicted != null)
            {
                mDetectedDataUnitID = predicted;
                mDuidCorrectionCount++;
            }
        }

        if(mDetectedDataUnitID != P25P1DataUnitID.PLACE_HOLDER)
        {
            mDetectedNAC = nac;
        }

        mDetectedSyncBitErrors = detectedBitErrors;

        //Strategy 5: Activate flywheel on valid NID decode, reset consecutive miss counter
        if(mDetectedDataUnitID != P25P1DataUnitID.PLACE_HOLDER)
        {
            mFlywheelActive = true;
            if(mFlywheelAssembly)
            {
                //A real sync arrived during flywheel assembly — count the flywheel as successful
                mFlywheelSuccessCount++;
                mFlywheelMissCount--;
            }
            mFlywheelConsecutiveMisses = 0;
            mFlywheelAssembly = false;
        }

        //If there is a message assembler (still) active, force it to complete
        if(mMessageAssembler != null)
        {
            if(mMessageAssembler.isComplete())
            {
                //If the message completed assembly as a place holder, that means a subsequent sync was not detected
                //and we should throw away placeholder message assembly, likely at the end of the transmission.
                if(mMessageAssembler.getDataUnitID() != P25P1DataUnitID.PLACE_HOLDER)
                {
                    dispatchMessage();
                }
            }
            else
            {
                int droppedBitCount = mMessageAssembler.forceCompletion(mPreviousDataUnitID, mDetectedDataUnitID);
                dispatchDroppedSamples(droppedBitCount);
                dispatchMessage();
            }
        }

        mDibitCounter -= 57;

        if(mDibitCounter > 0)
        {
            dispatchSyncLoss(mDibitCounter * 2);
        }
        else if(mDibitCounter < 0)
        {
            //Flip the sign on the dibit counter so we're sending a positive value
            dispatchDroppedSamples(-mDibitCounter * 2);
        }

        //Set dibit counter to 0 -- we'll start a message assembler once we skip the SYNC and NID dibits at dibit count=57
        mMessageAssemblyRequired = true;
        mDibitCounter = 57;
        mStatusSymbolDibitCounter = 21;
    }

    /**
     * Starts this framer dispatching messages
     */
    public void start()
    {
        mRunning = true;
    }

    /**
     * Stops this framer from dispatching messages
     */
    public void stop()
    {
        mRunning = false;
    }

    /**
     * Resets transmission-dependent state for cold-start scenarios.  Called when a new transmission is detected
     * after a period of silence.
     *
     * Note: We intentionally do NOT reset the NAC tracker here because on conventional PTT channels, the same
     * site transmits on the same frequency with the same NAC. Preserving the tracked NAC improves NID error
     * correction by providing NAC assist for the first few NIDs after a transmission boundary.
     */
    public void coldStartReset()
    {
        // NAC tracker intentionally NOT reset - same site/channel typically has same NAC
        // mNACTracker.reset();
    }

    /**
     * Sets the signal energy provider for adaptive sync threshold detection (Strategy 1).
     * @param provider that can report current signal energy state
     */
    public void setEnergyProvider(ISignalEnergyProvider provider)
    {
        mEnergyProvider = provider;
    }

    /**
     * Activates boundary recovery mode which uses hard sync detection in parallel
     * with soft sync for improved sync acquisition after transmission boundaries (Strategy 2).
     * @param active true to activate recovery mode
     */
    public void setBoundaryRecoveryActive(boolean active)
    {
        mBoundaryRecoveryActive = active;
        mRecoverySymbolCount = 0;
    }

    /**
     * Activates fade recovery mode which uses a lower sync threshold when
     * signal energy is fading at transmission end (Strategy 3).
     * @param active true to activate fade recovery
     */
    public void setFadeRecoveryActive(boolean active)
    {
        mFadeRecoveryActive = active;
    }

    /**
     * Activates initial acquisition mode which uses a lower, ramping sync threshold
     * during the first 100ms of a new transmission to recover weak preambles (Strategy 4).
     * @param active true to activate initial acquisition mode
     */
    public void setInitialAcquisitionActive(boolean active)
    {
        mInitialAcquisitionActive = active;
        mAcquisitionWindowSymbolCount = 0;
    }

    /**
     * Checks if initial acquisition mode is currently active.
     * @return true if in initial acquisition window
     */
    public boolean isInitialAcquisitionActive()
    {
        return mInitialAcquisitionActive;
    }

    /**
     * Sets a user-configured NAC value for this channel. When set, this NAC will be used for
     * NID error correction assistance, improving decode reliability on known channels.
     *
     * @param nac the configured NAC value, or 0 to use automatic tracking
     */
    public void setConfiguredNAC(int nac)
    {
        mNACTracker.setConfiguredNAC(nac);
    }

    /**
     * Gets the user-configured NAC value, or 0 if not configured.
     */
    public int getConfiguredNAC()
    {
        return mNACTracker.getConfiguredNAC();
    }

    /**
     * Predicts the next DUID based on the P25 superframe sequence.
     * Returns null if prediction is not possible (end of voice, control channel, unknown state).
     *
     * P25 voice superframe: HDU → LDU1 → LDU2 → LDU1 → LDU2 → ... → TDU/TDULC
     * The flywheel only predicts within the LDU1↔LDU2 alternation (both have identical frame length).
     */
    private P25P1DataUnitID predictNextDUID(P25P1DataUnitID previousDUID)
    {
        return switch(previousDUID)
        {
            case HEADER_DATA_UNIT -> P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1;
            case LOGICAL_LINK_DATA_UNIT_1 -> P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_2;
            case LOGICAL_LINK_DATA_UNIT_2 -> P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1;
            default -> null; //TDU, TDULC, TSBK, PDU, PLACEHOLDER — can't predict
        };
    }

    /**
     * Returns diagnostic statistics for analysis.
     */
    public String getDiagnostics()
    {
        double nidSuccessRate = mSyncDetectionCount > 0 ?
                (double) mNIDDecodeSuccessCount / mSyncDetectionCount * 100.0 : 0;
        return String.format("Sync: %d (initial: %d, fallback: %d, recovery: %d, fade: %d) | " +
                        "NID success: %d (%.1f%%) | NID fail: %d | " +
                        "Flywheel: %d attempts, %d success, %d miss",
                mSyncDetectionCount, mInitialAcquisitionSyncCount, mFallbackSyncCount,
                mRecoverySyncCount, mFadeRecoverySyncCount,
                mNIDDecodeSuccessCount, nidSuccessRate, mNIDDecodeFailCount,
                mFlywheelAttemptCount, mFlywheelSuccessCount, mFlywheelMissCount);
    }

    /**
     * Returns the count of fallback sync detections (Strategy 1).
     */
    public int getFallbackSyncCount()
    {
        return mFallbackSyncCount;
    }

    /**
     * Returns the count of recovery sync detections (Strategy 2).
     */
    public int getRecoverySyncCount()
    {
        return mRecoverySyncCount;
    }

    /**
     * Returns the count of fade recovery sync detections (Strategy 3).
     */
    public int getFadeRecoverySyncCount()
    {
        return mFadeRecoverySyncCount;
    }

    /**
     * Returns the count of initial acquisition sync detections (Strategy 4).
     */
    public int getInitialAcquisitionSyncCount()
    {
        return mInitialAcquisitionSyncCount;
    }

    /**
     * Returns the count of successful NID decodes.
     */
    public int getNIDDecodeSuccessCount()
    {
        return mNIDDecodeSuccessCount;
    }

    /**
     * Returns the count of failed NID decodes.
     */
    public int getNIDDecodeFailCount()
    {
        return mNIDDecodeFailCount;
    }

    /**
     * Returns the count of sync pattern detections.
     */
    public int getSyncDetectionCount()
    {
        return mSyncDetectionCount;
    }

    /**
     * Returns the count of sync detections that were blocked by the guard because a message
     * was still being assembled.
     */
    public int getSyncBlockedCount()
    {
        return mSyncBlockedCount;
    }

    /**
     * Sets the maximum BCH error corrections allowed for NAC-assisted/DUID-enumerated NID recovery.
     * Standard BCH decode always uses full T=11 capability. This threshold only applies to the
     * fallback NAC-forced and DUID-enumerated corrections, filtering out frames where heavy NID
     * correction indicates likely voice data corruption.
     *
     * @param maxErrors maximum allowed BCH corrections (1-11, default 11 = no filtering)
     */
    public void setMaxBchErrors(int maxErrors)
    {
        mMaxBchErrors = Math.max(1, Math.min(maxErrors, 11));
    }

    public int getMaxBchErrors()
    {
        return mMaxBchErrors;
    }

    public int getFlywheelAttemptCount()
    {
        return mFlywheelAttemptCount;
    }

    public int getFlywheelSuccessCount()
    {
        return mFlywheelSuccessCount;
    }

    public int getFlywheelMissCount()
    {
        return mFlywheelMissCount;
    }

    /**
     * Resets all diagnostic counters to zero. Call between file decodes.
     */
    public void resetDiagnostics()
    {
        mSyncDetectionCount = 0;
        mNIDDecodeSuccessCount = 0;
        mNIDDecodeFailCount = 0;
        mSyncBlockedCount = 0;
        mFallbackSyncCount = 0;
        mRecoverySyncCount = 0;
        mFadeRecoverySyncCount = 0;
        mInitialAcquisitionSyncCount = 0;
        mFlywheelAttemptCount = 0;
        mFlywheelSuccessCount = 0;
        mFlywheelMissCount = 0;
    }

    /**
     * Sets the listener to receive framed DMR messages.
     * @param listener for messages.
     */
    public void setListener(Listener<IMessage> listener)
    {
        mMessageListener = listener;
    }

    /**
     * Sets or updates the current dibit stream time from an incoming sample buffer.
     * @param time to use as a reference timestamp.
     */
    public void setTimestamp(long time)
    {
        mReferenceTimestamp = time;
        mDibitSinceTimestampCounter = 0;
    }

    /**
     * Calculates the timestamp accurate to the currently received dibit.
     * @return timestamp in milliseconds.
     */
    private long getTimestamp()
    {
        if(mReferenceTimestamp > 0)
        {
            return mReferenceTimestamp + (long)(1000.0 * mDibitSinceTimestampCounter / 4800);
        }
        else
        {
            mDibitSinceTimestampCounter = 0;
            return System.currentTimeMillis();
        }
    }

    private boolean checkNID()
    {
        CorrectedBinaryMessage nid = new CorrectedBinaryMessage((DIBIT_LENGTH_NID - 1) * 2);

        Dibit dibit;

        for(int i = 0; i < DIBIT_LENGTH_NID; i++)
        {
            if(i != 11)
            {
                dibit = mNIDBuffer[i];
                nid.add(dibit.getBit1(), dibit.getBit2());
            }
        }


        int trackedNAC = mNACTracker.getTrackedNAC();
        mBCHDecoder.decode(nid, trackedNAC, mMaxBchErrors);

        int nac = nid.getInt(NAC_FIELD);
        P25P1DataUnitID duid = P25P1DataUnitID.fromValue(nid.getInt(DUID_FIELD));

        //If error correction fails, return the original correction candidate
        if(nid.getCorrectedBitCount() < 0)
        {
            mNIDDecodeFailCount++;
            return false;
        }

        mNIDDecodeSuccessCount++;

        //The BCH decoder can over-correct the NID and produce an invalid NAC.  Compare it against the tracked NAC to
        //flag it as invalid NID when this happens.  The NAC tracker will give us a value of 0 until it has enough
        //observations of a valid NID value.
        mNACTracker.track(nac);
//        System.out.println("\t\t" + mDebugSymbolCount + " VALID NID - NAC:" + nac + " DUID:" + duid);
        nidDetected(nac, duid, nid.getCorrectedBitCount());
        return true;
    }
}
