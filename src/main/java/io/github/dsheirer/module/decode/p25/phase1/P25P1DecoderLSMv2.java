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

import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.decimate.DecimationFilterFactory;
import io.github.dsheirer.dsp.filter.decimate.IRealDecimationFilter;
import io.github.dsheirer.dsp.filter.fir.FIRFilterSpecification;
import io.github.dsheirer.dsp.filter.fir.real.IRealFilter;
import io.github.dsheirer.dsp.filter.fir.real.RealFIRFilter;
import io.github.dsheirer.dsp.squelch.PowerMonitor;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.FeedbackDecoder;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.buffer.IByteBufferProvider;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.sample.complex.IComplexSamplesListener;
import io.github.dsheirer.source.ISourceEventListener;
import io.github.dsheirer.source.ISourceEventProvider;
import io.github.dsheirer.source.SourceEvent;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * APCO25 Phase 1 LSM v2 decoder with improved cold-start handling for conventional (PTT) channels.
 *
 * This decoder is functionally identical to P25P1DecoderLSM in terms of pipeline structure (decimation, baseband
 * filter, pulse shaping, demodulation) but uses P25P1DemodulatorLSMv2 which adds transmission boundary detection
 * and adaptive cold-start behavior for channels where the carrier turns on and off with each transmission.
 */
public class P25P1DecoderLSMv2 extends FeedbackDecoder implements IByteBufferProvider, IComplexSamplesListener,
        ISourceEventListener, ISourceEventProvider, Listener<ComplexSamples>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(P25P1DecoderLSMv2.class);
    private static final Map<Double,float[]> BASEBAND_FILTERS = new HashMap<>();
    private static final int SYMBOL_RATE = 4800;

    // Transmission boundary detection constants
    private static final float ENERGY_EMA_FACTOR = 0.001f;
    private static final float ENERGY_SILENCE_RATIO = 0.1f; // silence = below 10% of peak
    private static final double SILENCE_DURATION_SECONDS = 1.0;

    private final P25P1DemodulatorLSMv2 mDemodulator;
    private final P25P1MessageFramer mMessageFramer = new P25P1MessageFramer();
    private final P25P1MessageProcessor mMessageProcessor = new P25P1MessageProcessor();
    private final PowerMonitor mPowerMonitor = new PowerMonitor();
    private IRealDecimationFilter mDecimationFilterI;
    private IRealDecimationFilter mDecimationFilterQ;
    private IRealFilter mBasebandFilterI;
    private IRealFilter mBasebandFilterQ;
    private IRealFilter mPulseShapingFilterI;
    private IRealFilter mPulseShapingFilterQ;

    // Transmission boundary detection state
    private float mEnergyAverage = 0f;
    private float mPeakEnergy = 0f;
    private int mSilenceSampleCount = 0;
    private int mSilenceSamplesThreshold = 12500;
    private boolean mInSilence = true;
    private int mBoundaryResetCount = 0;

    @Override
    public DecoderType getDecoderType()
    {
        return DecoderType.P25_PHASE1;
    }

    public P25P1DecoderLSMv2()
    {
        mMessageProcessor.setMessageListener(getMessageListener());
        mDemodulator = new P25P1DemodulatorLSMv2(mMessageFramer, this);
    }

    @Override
    public String getProtocolDescription()
    {
        return "P25 Phase 1 LSM v2";
    }

    /**
     * Sets the sample rate and configures internal components.
     * @param sampleRate of the channel to decode
     */
    public void setSampleRate(double sampleRate)
    {
        if(sampleRate <= SYMBOL_RATE * 2)
        {
            throw new IllegalArgumentException("Sample rate [" + sampleRate + "] must be >9600 (2 * " +
                    SYMBOL_RATE + " symbol rate)");
        }

        mPowerMonitor.setSampleRate((int)sampleRate);

        int decimation = 1;

        //Identify decimation that gets as close to 4.0 Samples Per Symbol as possible (4800 x 4.0 = 19.2 kHz)
        while((sampleRate / decimation) >= 38400)
        {
            decimation *= 2;
        }

        try
        {
            mDecimationFilterI = DecimationFilterFactory.getRealDecimationFilter(decimation);
            mDecimationFilterQ = DecimationFilterFactory.getRealDecimationFilter(decimation);
        }
        catch(Exception e)
        {
            LOGGER.error("Error getting decimation filter for sample rate [" + sampleRate + "] decimation [" + decimation + "]");
        }

        float decimatedSampleRate = (float)sampleRate / decimation;
        int symbolLength = 16;
        float rolloff = 0.2f;

        float[] taps = FilterFactory.getRootRaisedCosine(decimatedSampleRate / 4800.0, symbolLength, rolloff);
        mPulseShapingFilterI = new RealFIRFilter(taps);
        mPulseShapingFilterQ = new RealFIRFilter(taps);
        mBasebandFilterI = FilterFactory.getRealFilter(getBasebandFilter(decimatedSampleRate));
        mBasebandFilterQ = FilterFactory.getRealFilter(getBasebandFilter(decimatedSampleRate));
        mDemodulator.setSamplesPerSymbol(decimatedSampleRate / (float)SYMBOL_RATE);
        mSilenceSamplesThreshold = (int)(decimatedSampleRate * SILENCE_DURATION_SECONDS);
        mMessageFramer.setListener(mMessageProcessor);
    }

    /**
     * Primary method for processing incoming complex sample buffers
     * @param samples containing channelized complex samples
     */
    @Override
    public void receive(ComplexSamples samples)
    {
        //Update the message framer with the timestamp from the incoming sample buffer.
        mMessageFramer.setTimestamp(samples.timestamp());

        float[] i = samples.i();
        float[] q = samples.q();

        i = mDecimationFilterI.decimateReal(i);
        q = mDecimationFilterQ.decimateReal(q);

        //Detect transmission boundaries on raw decimated samples (before filtering distorts energy levels)
        detectTransmissionBoundary(i, q);

        //Process buffer for power measurements
        mPowerMonitor.process(i, q);

        i = mBasebandFilterI.filter(i);
        q = mBasebandFilterQ.filter(q);

        i = mPulseShapingFilterI.filter(i);
        q = mPulseShapingFilterQ.filter(q);

        //Demodulate samples into symbols with timing, sync detection, and message framing.
        mDemodulator.process(i, q);
    }

    /**
     * Monitors energy on raw decimated I/Q samples to detect transmission boundaries.
     * Uses a dynamic threshold: silence is when energy drops below 10% of the observed peak.
     * When sustained silence is followed by signal return, triggers a cold-start reset.
     */
    private void detectTransmissionBoundary(float[] i, float[] q)
    {
        for(int idx = 0; idx < i.length; idx++)
        {
            float energy = (i[idx] * i[idx]) + (q[idx] * q[idx]);
            mEnergyAverage += (energy - mEnergyAverage) * ENERGY_EMA_FACTOR;

            // Track peak energy with slow decay (adapts to signal level)
            if(mEnergyAverage > mPeakEnergy)
            {
                mPeakEnergy = mEnergyAverage;
            }
            else
            {
                // Slow decay: ~10 second time constant at 25 kHz
                mPeakEnergy *= 0.99999f;
            }

            float silenceThreshold = mPeakEnergy * ENERGY_SILENCE_RATIO;

            if(mPeakEnergy > 0 && mEnergyAverage < silenceThreshold)
            {
                mSilenceSampleCount++;
                if(mSilenceSampleCount >= mSilenceSamplesThreshold)
                {
                    mInSilence = true;
                }
            }
            else
            {
                if(mInSilence && mPeakEnergy > 0)
                {
                    // Transition from silence to signal — new transmission starting
                    mDemodulator.coldStartReset();
                    mMessageFramer.coldStartReset();
                    mBoundaryResetCount++;
                    mInSilence = false;
                }
                mSilenceSampleCount = 0;
            }
        }
    }

    /**
     * Constructs a baseband filter for this decoder using the current sample rate
     */
    private float[] getBasebandFilter(double sampleRate)
    {
        if(BASEBAND_FILTERS.containsKey(sampleRate))
        {
            return BASEBAND_FILTERS.get(sampleRate);
        }

        FIRFilterSpecification specification = FIRFilterSpecification
                .lowPassBuilder()
                .sampleRate(sampleRate)
                .passBandCutoff(7250)
                .passBandAmplitude(1.0).passBandRipple(0.01)
                .stopBandAmplitude(0.0).stopBandStart(8000)
                .stopBandRipple(0.01).build();

        float[] coefficients = null;

        try
        {
            coefficients = FilterFactory.getTaps(specification);
            BASEBAND_FILTERS.put(sampleRate, coefficients);
        }
        catch(Exception fde)
        {
            LOGGER.error("Error designing baseband filter for sample rate [" + sampleRate + "]");
        }

        if(coefficients == null)
        {
            throw new IllegalStateException("Unable to design low pass filter for sample rate [" + sampleRate + "]");
        }

        return coefficients;
    }

    @Override
    public void setBufferListener(Listener<ByteBuffer> listener)
    {
        mDemodulator.setBufferListener(listener);
    }

    @Override
    public void removeBufferListener(Listener<ByteBuffer> listener)
    {
        mDemodulator.setBufferListener(null);
    }

    @Override
    public boolean hasBufferListeners()
    {
        return mDemodulator.hasBufferListener();
    }

    @Override
    public Listener<SourceEvent> getSourceEventListener()
    {
        return this::process;
    }

    @Override
    public void setSourceEventListener(Listener<SourceEvent> listener)
    {
        super.setSourceEventListener(listener);
        mPowerMonitor.setSourceEventListener(listener);
    }

    @Override
    public void removeSourceEventListener()
    {
        mPowerMonitor.setSourceEventListener(null);
    }

    @Override
    public void start()
    {
        super.start();
        mMessageFramer.start();
    }

    @Override
    public void stop()
    {
        super.stop();
        mMessageFramer.stop();
    }

    /**
     * Process source events
     */
    private void process(SourceEvent sourceEvent)
    {
        switch(sourceEvent.getEvent())
        {
            case NOTIFICATION_FREQUENCY_CHANGE:
            case NOTIFICATION_FREQUENCY_CORRECTION_CHANGE:
                mDemodulator.resetPLL();
                break;
            case NOTIFICATION_SAMPLE_RATE_CHANGE:
                setSampleRate(sourceEvent.getValue().doubleValue());
                break;
        }
    }

    @Override
    public Listener<ComplexSamples> getComplexSamplesListener()
    {
        return this;
    }

    /**
     * Returns diagnostic statistics from the v2 demodulator for testing/tuning.
     */
    public String getDiagnostics()
    {
        String demodDiag = mDemodulator.getDiagnostics();
        String framerDiag = mMessageFramer.getDiagnostics();
        return String.format("Boundary resets: %d | %s | %s",
                mBoundaryResetCount, demodDiag, framerDiag);
    }

    /**
     * Returns the message framer for diagnostic access.
     */
    public P25P1MessageFramer getMessageFramer()
    {
        return mMessageFramer;
    }

    /**
     * Returns the demodulator for diagnostic access.
     */
    public P25P1DemodulatorLSMv2 getDemodulator()
    {
        return mDemodulator;
    }

    /**
     * Sets a user-configured NAC value for this channel. When set, this NAC will be used for
     * NID error correction assistance, improving decode reliability on known channels.
     *
     * @param nac the configured NAC value (0-4095), or 0 to use automatic tracking
     */
    public void setConfiguredNAC(int nac)
    {
        mMessageFramer.setConfiguredNAC(nac);
    }

    /**
     * Gets the user-configured NAC value, or 0 if not configured.
     */
    public int getConfiguredNAC()
    {
        return mMessageFramer.getConfiguredNAC();
    }
}
