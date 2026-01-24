/*
 * *****************************************************************************
 * Copyright (C) 2014-2023 Dennis Sheirer
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

import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.dsp.squelch.CTCSSFrequency;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.string.SimpleStringIdentifier;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.analog.AnalogDecoderState;
import io.github.dsheirer.module.decode.event.DecodeEvent;
import io.github.dsheirer.module.decode.event.DecodeEventType;
import io.github.dsheirer.protocol.Protocol;

/**
 * NBFM decoder state
 */
public class NBFMDecoderState extends AnalogDecoderState
{
    private String mChannelName;
    private Identifier mChannelNameIdentifier;
    private Identifier mTalkgroupIdentifier;
    private CTCSSFrequency mCTCSSFrequency;

    /**
     * Constructs an instance
     * @param channelName to use for this channel
     * @param decodeConfig with talkgroup identifier
     */
    public NBFMDecoderState(String channelName, DecodeConfigNBFM decodeConfig)
    {
        mChannelName = (channelName != null && !channelName.isEmpty()) ? channelName : "NBFM CHANNEL";
        mChannelNameIdentifier = new SimpleStringIdentifier(mChannelName, IdentifierClass.CONFIGURATION, Form.CHANNEL_NAME, Role.ANY);
        mTalkgroupIdentifier = new NBFMTalkgroup(decodeConfig.getTalkgroup());
        mCTCSSFrequency = decodeConfig.getCTCSSFrequency();
    }

    @Override
    public void receiveDecoderStateEvent(DecoderStateEvent event)
    {
        super.receiveDecoderStateEvent(event);

        if(event.getEvent() == DecoderStateEvent.Event.DECODE && mCTCSSFrequency != null &&
                mCTCSSFrequency != CTCSSFrequency.NONE)
        {
            DecodeEvent toneEvent = DecodeEvent.builder(DecodeEventType.TONE_DETECT, System.currentTimeMillis())
                    .details("CTCSS " + mCTCSSFrequency.toString())
                    .identifiers(new IdentifierCollection(getIdentifierCollection().getIdentifiers()))
                    .protocol(Protocol.NBFM)
                    .build();
            broadcast(toneEvent);
        }
    }

    @Override
    public DecoderType getDecoderType()
    {
        return DecoderType.NBFM;
    }

    @Override
    protected Identifier getChannelNameIdentifier()
    {
        return mChannelNameIdentifier;
    }

    @Override
    protected Identifier getTalkgroupIdentifier()
    {
        return mTalkgroupIdentifier;
    }

    @Override
    public String getActivitySummary()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Activity Summary\n");
        sb.append("\tDecoder: NBFM");
        sb.append("\n\n");
        return sb.toString();
    }
}
