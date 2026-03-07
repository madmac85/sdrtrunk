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

package io.github.dsheirer.module.decode.p25.phase1.message.ldu;

import io.github.dsheirer.bits.BinaryMessage;
import io.github.dsheirer.edac.Golay23;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic analysis of IMBE voice frames within P25 LDU messages.
 *
 * Replicates JMBE's internal FEC pipeline (deinterleave → Golay(23,12) → derandomize → Hamming(15,11))
 * to count errors per codeword without modifying the production decode path.
 *
 * This enables quality scoring of IMBE frames before they reach the codec, supporting
 * pre-codec quality gating for simulcast channels with high error rates.
 */
public class IMBEFrameDiagnostic
{
    /**
     * JMBE's IMBE deinterleave pattern (from jmbe/codec/src/main/java/jmbe/codec/imbe/IMBEInterleave.java).
     * Maps interleaved bit position → deinterleaved bit position for 144-bit IMBE frames.
     * NOTE: This is different from SDRTrunk's P25P1Interleave.VOICE_DEINTERLEAVE.
     */
    private static final int[] DEINTERLEAVE = {
        0, 24, 48, 72, 96, 120, 25, 1, 73, 49, 121, 97, 2, 26, 50, 74, 98, 122, 27, 3, 75, 51, 123, 99,
        4, 28, 52, 76, 100, 124, 29, 5, 77, 53, 125, 101, 6, 30, 54, 78, 102, 126, 31, 7, 79, 55, 127, 103,
        8, 32, 56, 80, 104, 128, 33, 9, 81, 57, 129, 105, 10, 34, 58, 82, 106, 130, 35, 11, 83, 59, 131, 107,
        12, 36, 60, 84, 108, 132, 37, 13, 85, 61, 133, 109, 14, 38, 62, 86, 110, 134, 39, 15, 87, 63, 135, 111,
        16, 40, 64, 88, 112, 136, 41, 17, 89, 65, 137, 113, 18, 42, 66, 90, 114, 138, 43, 19, 91, 67, 139, 115,
        20, 44, 68, 92, 116, 140, 45, 21, 93, 69, 141, 117, 22, 46, 70, 94, 118, 142, 47, 23, 95, 71, 143, 119
    };

    /**
     * JMBE's Hamming(15,11) checksums for IMBE voice frames.
     * NOTE: Different from SDRTrunk's Hamming15 which uses DMR checksums.
     * These are the standard Hamming(15,11) checksums: {0xF, 0xE, 0xD, 0xC, 0xB, 0xA, 0x9, 0x7, 0x6, 0x5, 0x3}
     */
    private static final int[] HAMMING15_CHECKSUMS = {0xF, 0xE, 0xD, 0xC, 0xB, 0xA, 0x9, 0x7, 0x6, 0x5, 0x3};

    /**
     * Bit indices for the randomizer seed (first 12 bits of deinterleaved coset c0).
     */
    private static final int[] RANDOMIZER_SEED = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    /**
     * Maximum number of valid IMBE fundamental frequency values (0-206).
     * Value 207+ maps to INVALID in JMBE's IMBEFundamentalFrequency enum.
     */
    private static final int MAX_VALID_FUNDAMENTAL = 206;

    /**
     * Bit indices for the b0 vector (fundamental frequency), from JMBE's IMBEFrame.VECTOR_B0.
     */
    private static final int[] VECTOR_B0 = {0, 1, 2, 3, 4, 5, 141, 142};

    /**
     * Per-frame error analysis result.
     *
     * @param codewordErrors error counts for 7 codewords: [0-3]=Golay(23,12), [4-6]=Hamming(15,11)
     *                       Golay: 0-3 = corrected errors, 4 = uncorrectable
     *                       Hamming: 0 = no errors, 1 = corrected, 2 = uncorrectable (multi-bit)
     * @param totalErrors sum of all codeword error counts
     * @param uncorrectableCount number of codewords with uncorrectable errors (Golay=4 or Hamming=2)
     * @param fundamentalValid true if the decoded fundamental frequency index is in valid range (0-206)
     */
    public record FrameErrors(int[] codewordErrors, int totalErrors, int uncorrectableCount,
                              boolean fundamentalValid)
    {
        @Override
        public String toString()
        {
            return String.format("errors=%s total=%d uncorrectable=%d fundValid=%b",
                Arrays.toString(codewordErrors), totalErrors, uncorrectableCount, fundamentalValid);
        }
    }

    /**
     * LDU-level diagnostic summary across all 9 IMBE frames.
     *
     * @param frameErrors per-frame error analysis for all 9 frames
     * @param totalErrors total errors across all frames
     * @param uncorrectableFrameCount number of frames with at least one uncorrectable codeword
     * @param invalidFundamentalCount number of frames with invalid fundamental frequency
     * @param maxFrameErrors highest single-frame error count
     */
    public record LDUErrors(List<FrameErrors> frameErrors, int totalErrors, int uncorrectableFrameCount,
                            int invalidFundamentalCount, int maxFrameErrors)
    {
        @Override
        public String toString()
        {
            return String.format("LDU: totalErrors=%d uncorrectableFrames=%d/%d invalidFund=%d maxFrame=%d",
                totalErrors, uncorrectableFrameCount, frameErrors.size(), invalidFundamentalCount, maxFrameErrors);
        }
    }

    /**
     * Analyzes a single IMBE frame for FEC error counts.
     *
     * Replicates JMBE's internal decode pipeline:
     * 1. Deinterleave using JMBE's pattern
     * 2. Golay(23,12) error correction on coset c0
     * 3. Derandomize cosets c1-c6 using PRNG seeded from c0
     * 4. Golay(23,12) error correction on cosets c1, c2, c3
     * 5. Hamming(15,11) error detection/correction on cosets c4, c5, c6
     *
     * @param frameData raw IMBE frame bytes from LDUMessage.getIMBEFrames() (up to 18 bytes for 144 bits)
     * @return error analysis for this frame
     */
    public static FrameErrors analyzeFrame(byte[] frameData)
    {
        // Pad to 18 bytes (BitSet.toByteArray() may truncate trailing zero bytes)
        byte[] padded = new byte[18];
        System.arraycopy(frameData, 0, padded, 0, Math.min(frameData.length, 18));

        // Convert bytes to BinaryMessage using MSB-first byte ordering (matches JMBE's fromBytes LITTLE_ENDIAN)
        BinaryMessage frame = BinaryMessage.from(padded);

        // Step 1: Deinterleave
        deinterleave(frame);

        // Step 2: Golay(23,12) on coset c0 (bits 0-22)
        int[] errors = new int[7];
        errors[0] = Golay23.checkAndCorrect(frame, 0);

        // Step 3: Derandomize cosets c1-c6 using PRNG seeded from corrected c0 data
        derandomize(frame);

        // Step 4: Golay(23,12) on cosets c1-c3
        errors[1] = Golay23.checkAndCorrect(frame, 23);
        errors[2] = Golay23.checkAndCorrect(frame, 46);
        errors[3] = Golay23.checkAndCorrect(frame, 69);

        // Step 5: Hamming(15,11) on cosets c4-c6
        errors[4] = hamming15CheckAndCorrect(frame, 92);
        errors[5] = hamming15CheckAndCorrect(frame, 107);
        errors[6] = hamming15CheckAndCorrect(frame, 122);

        // Calculate totals
        int totalErrors = 0;
        int uncorrectable = 0;
        for(int i = 0; i < 7; i++)
        {
            totalErrors += errors[i];
            if((i < 4 && errors[i] > 3) || (i >= 4 && errors[i] > 1))
            {
                uncorrectable++;
            }
        }

        // Check fundamental frequency validity
        boolean fundamentalValid = isFundamentalValid(frame);

        return new FrameErrors(errors, totalErrors, uncorrectable, fundamentalValid);
    }

    /**
     * Analyzes all 9 IMBE frames in an LDU message.
     *
     * @param ldu LDU message containing 9 IMBE voice frames
     * @return LDU-level error diagnostic
     */
    public static LDUErrors analyzeLDU(LDUMessage ldu)
    {
        List<byte[]> imbeFrames = ldu.getIMBEFrames();
        List<FrameErrors> frameErrors = new ArrayList<>(9);
        int totalErrors = 0;
        int uncorrectableFrames = 0;
        int invalidFundamentals = 0;
        int maxFrameErrors = 0;

        for(byte[] frameData : imbeFrames)
        {
            FrameErrors fe = analyzeFrame(frameData);
            frameErrors.add(fe);
            totalErrors += fe.totalErrors();
            if(fe.uncorrectableCount() > 0)
            {
                uncorrectableFrames++;
            }
            if(!fe.fundamentalValid())
            {
                invalidFundamentals++;
            }
            if(fe.totalErrors() > maxFrameErrors)
            {
                maxFrameErrors = fe.totalErrors();
            }
        }

        return new LDUErrors(frameErrors, totalErrors, uncorrectableFrames, invalidFundamentals, maxFrameErrors);
    }

    /**
     * Applies JMBE's deinterleave pattern to a 144-bit IMBE frame.
     */
    private static void deinterleave(BinaryMessage frame)
    {
        java.util.BitSet original = frame.get(0, 144);
        frame.clear(0, 144);
        for(int i = original.nextSetBit(0); i >= 0 && i < DEINTERLEAVE.length; i = original.nextSetBit(i + 1))
        {
            frame.set(DEINTERLEAVE[i]);
        }
    }

    /**
     * Removes randomizer by generating a PRNG sequence from the first 12 bits of corrected coset c0
     * and XORing it against cosets c1 through c6 (bits 23-136).
     * Replicates JMBE's IMBEFrame.derandomize() algorithm.
     */
    private static void derandomize(BinaryMessage frame)
    {
        // Get seed from first 12 bits of coset c0 (after Golay correction)
        int seed = getInt(frame, RANDOMIZER_SEED);
        int prX = 16 * seed;

        // Apply PRNG to bits 23-136 (cosets c1 through c6)
        for(int x = 0; x < 114; x++)
        {
            prX = (173 * prX + 13849) % 65536;
            if(prX >= 32768)
            {
                frame.flip(x + 23);
            }
        }
    }

    /**
     * IMBE-specific Hamming(15,11) error detection and correction.
     * Uses JMBE's checksums (different from SDRTrunk's DMR Hamming15).
     *
     * @return 0 = no errors, 1 = single-bit corrected, 2 = uncorrectable multi-bit error
     */
    private static int hamming15CheckAndCorrect(BinaryMessage frame, int startIndex)
    {
        int syndrome = hamming15Syndrome(frame, startIndex);

        if(syndrome == 0)
        {
            return 0;
        }

        // Syndrome 1-15 all indicate single-bit errors that can be corrected
        // Map syndrome to bit position and correct
        int bitIndex = switch(syndrome)
        {
            case 1 -> startIndex + 14;
            case 2 -> startIndex + 13;
            case 3 -> startIndex + 10;
            case 4 -> startIndex + 12;
            case 5 -> startIndex + 9;
            case 6 -> startIndex + 8;
            case 7 -> startIndex + 7;
            case 8 -> startIndex + 11;
            case 9 -> startIndex + 6;
            case 10 -> startIndex + 5;
            case 11 -> startIndex + 4;
            case 12 -> startIndex + 3;
            case 13 -> startIndex + 2;
            case 14 -> startIndex + 1;
            case 15 -> startIndex;
            default -> -1; // Can't happen with 4-bit syndrome
        };

        if(bitIndex >= 0)
        {
            frame.flip(bitIndex);
            return 1;
        }

        return 2;
    }

    /**
     * Calculates Hamming(15,11) syndrome using JMBE's IMBE checksums.
     */
    private static int hamming15Syndrome(BinaryMessage frame, int startIndex)
    {
        int calculated = 0;
        for(int i = frame.nextSetBit(startIndex); i >= startIndex && i < startIndex + 11; i = frame.nextSetBit(i + 1))
        {
            calculated ^= HAMMING15_CHECKSUMS[i - startIndex];
        }

        // Get the transmitted checksum from bits 11-14
        int checksum = 0;
        for(int i = startIndex + 11; i <= startIndex + 14; i++)
        {
            checksum = (checksum << 1) | (frame.get(i) ? 1 : 0);
        }

        return checksum ^ calculated;
    }

    /**
     * Reads an integer value from specified bit positions (MSB first), matching JMBE's getInt(int[]).
     */
    private static int getInt(BinaryMessage frame, int[] bits)
    {
        int value = 0;
        for(int index : bits)
        {
            value = (value << 1) | (frame.get(index) ? 1 : 0);
        }
        return value;
    }

    /**
     * Checks whether the decoded fundamental frequency index is in valid range (0-206).
     * JMBE's IMBEFundamentalFrequency enum maps values 0-206 to valid frequencies;
     * values >= 207 map to INVALID.
     */
    private static boolean isFundamentalValid(BinaryMessage frame)
    {
        int b0 = getInt(frame, VECTOR_B0);
        return b0 <= MAX_VALID_FUNDAMENTAL;
    }
}
