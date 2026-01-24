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
package io.github.dsheirer.dsp.squelch;

/**
 * Standard EIA/TIA CTCSS (Continuous Tone-Coded Squelch System) tone frequencies,
 * also known as PL (Private Line) tones.
 */
public enum CTCSSFrequency
{
    NONE("None", 0.0),
    TONE_67_0("67.0 Hz", 67.0),
    TONE_69_3("69.3 Hz", 69.3),
    TONE_71_9("71.9 Hz", 71.9),
    TONE_74_4("74.4 Hz", 74.4),
    TONE_77_0("77.0 Hz", 77.0),
    TONE_79_7("79.7 Hz", 79.7),
    TONE_82_5("82.5 Hz", 82.5),
    TONE_85_4("85.4 Hz", 85.4),
    TONE_88_5("88.5 Hz", 88.5),
    TONE_91_5("91.5 Hz", 91.5),
    TONE_94_8("94.8 Hz", 94.8),
    TONE_97_4("97.4 Hz", 97.4),
    TONE_100_0("100.0 Hz", 100.0),
    TONE_103_5("103.5 Hz", 103.5),
    TONE_107_2("107.2 Hz", 107.2),
    TONE_110_9("110.9 Hz", 110.9),
    TONE_114_8("114.8 Hz", 114.8),
    TONE_118_8("118.8 Hz", 118.8),
    TONE_123_0("123.0 Hz", 123.0),
    TONE_127_3("127.3 Hz", 127.3),
    TONE_131_8("131.8 Hz", 131.8),
    TONE_136_5("136.5 Hz", 136.5),
    TONE_141_3("141.3 Hz", 141.3),
    TONE_146_2("146.2 Hz", 146.2),
    TONE_151_4("151.4 Hz", 151.4),
    TONE_156_7("156.7 Hz", 156.7),
    TONE_162_2("162.2 Hz", 162.2),
    TONE_167_9("167.9 Hz", 167.9),
    TONE_173_8("173.8 Hz", 173.8),
    TONE_179_9("179.9 Hz", 179.9),
    TONE_186_2("186.2 Hz", 186.2),
    TONE_192_8("192.8 Hz", 192.8),
    TONE_203_5("203.5 Hz", 203.5),
    TONE_210_7("210.7 Hz", 210.7),
    TONE_218_1("218.1 Hz", 218.1),
    TONE_225_7("225.7 Hz", 225.7),
    TONE_233_6("233.6 Hz", 233.6),
    TONE_241_8("241.8 Hz", 241.8),
    TONE_250_3("250.3 Hz", 250.3);

    private final String mLabel;
    private final double mFrequency;

    /**
     * Constructs an instance
     * @param label for display
     * @param frequency in Hz
     */
    CTCSSFrequency(String label, double frequency)
    {
        mLabel = label;
        mFrequency = frequency;
    }

    /**
     * Frequency value in Hz
     * @return frequency
     */
    public double getFrequency()
    {
        return mFrequency;
    }

    @Override
    public String toString()
    {
        return mLabel;
    }
}
