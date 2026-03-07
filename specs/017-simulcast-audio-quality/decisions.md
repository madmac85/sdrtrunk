# Decisions: Simulcast Audio Quality Improvement

## 2026-03-07 - Candidate A Invalidated: JMBE Already Handles Voice FEC
**Context**: Task 1.1 research into IMBE frame structure and JMBE codec internals.
**Decision**: Candidate A (pre-codec voice FEC) is not viable as originally conceived.
**Rationale**: JMBE already performs deinterleave → Golay(23,12) ×4 → derandomize → Hamming(15,11) ×3 internally on every IMBE frame. Applying FEC before JMBE would double-process (neutral at best, harmful if deinterleave applied twice). However, replicating JMBE's FEC diagnostically for quality gating (Candidate B) remains valid.
**Alternatives Considered**: (1) Apply FEC before JMBE — rejected because JMBE expects raw interleaved frames. (2) Modify JMBE itself — out of scope per spec. (3) Use diagnostic FEC to score quality — adopted as basis for Candidate B.

## 2026-03-07 - Use JMBE's FEC Algorithms for Diagnostic, Not SDRTrunk's
**Context**: SDRTrunk has its own Golay23, Hamming15, and VOICE_DEINTERLEAVE, but these don't all match JMBE's versions.
**Decision**: The diagnostic must replicate JMBE's exact FEC algorithms (deinterleave pattern, Hamming15 checksums) rather than using SDRTrunk's existing EDAC classes for Hamming.
**Rationale**:
- SDRTrunk's `Golay23` is identical to JMBE's — safe to reuse
- SDRTrunk's `Hamming15` uses DMR checksums (`{0x9,0xD,...}`) — JMBE uses IMBE checksums (`{0xF,0xE,...}`)
- SDRTrunk's `VOICE_DEINTERLEAVE` pattern is completely different from JMBE's `DEINTERLEAVE`
- Using SDRTrunk's versions would produce incorrect error counts that don't match what JMBE sees
**Alternatives Considered**: Using SDRTrunk's existing classes — rejected due to checksum/pattern mismatches.

## 2026-03-07 - Use BinaryMessage.from() Not Constructor for Byte Conversion
**Context**: IMBE frames are extracted as byte[] via `BitSet.toByteArray()` (LSB-first), then JMBE loads them with `setByte()` (MSB-first).
**Decision**: Always use `BinaryMessage.from(byte[])` (MSB-first, matches JMBE) not `new BinaryMessage(byte[])` (LSB-first, mismatches).
**Rationale**: The byte[] from `getIMBEFrames()` is packed LSB-first by `BitSet.toByteArray()`. JMBE's `fromBytes(data, LITTLE_ENDIAN)` unpacks MSB-first via `setByte()`. SDRTrunk's `BinaryMessage.from()` also uses `setByte()` — matching JMBE's behavior. The constructor uses `BitSet.valueOf()` which is LSB-first — wrong for IMBE processing.

## 2026-03-07 - BCH T=5 Optimal Threshold for NAC-Assisted Corrections
**Context**: DUID enumeration accepts NID corrections with varying BCH error counts. Testing all thresholds (T=3,5,8,11).
**Decision**: T=5 is the optimal BCH error threshold for NAC-assisted/DUID-enumerated corrections.
**Rationale**: STT word count comparison on LFD simulcast:
- T=3+B3: 116 words (too restrictive, less coverage)
- T=5+B3: 173 words (BEST — +25% vs baseline)
- T=8+B3: 110 words (accepts bad DUIDs that corrupt framer state)
- T=11+B3: 21 words (catastrophic — most frames silenced)
- T=11 no gate: only 150 words vs 138 baseline (wrong DUIDs cause framer state corruption)
**Alternatives Considered**: T=3 (conservative), T=8 (moderate), T=11 (no restriction). All tested and inferior.

## 2026-03-07 - Pre-Codec IMBE Quality Gate (maxImbeErrors=3)
**Context**: JMBE codec accumulates error state across frames. Bad frames contaminate codec state, degrading subsequent frames.
**Decision**: Add pre-codec quality gate that checks IMBE FEC errors before passing frames to JMBE. Frames with >3 total errors are replaced with silence, bypassing the codec entirely.
**Rationale**: STT comparison on LFD:
- T=5 without gate: 122 words
- T=5 with gate (maxImbe=3): 173 words (+42%)
- Gate prevents bad frames from contaminating JMBE's running error rate
- maxImbe=6 too lenient (79 words), maxImbe=3 is optimal for the bimodal error distribution (most frames are 0-3 or 11+ errors)
**Alternatives Considered**: Post-codec concealment (already exists, energy-based), per-frame repeat (complex state management). Pre-codec gate is simpler and more accurate.

## 2026-03-07 - Auto-Enable for Simulcast Channels Only
**Context**: Quality gate and BCH threshold could help or hurt depending on channel type.
**Decision**: Auto-enable both features only for CQPSK_V2 decoder with configured NAC (simulcast channels). C4FM and CQPSK_V1 channels are unaffected.
**Rationale**: C4FM channels have clean frames (0-3 errors typically) — quality gate unnecessary. CQPSK_V1 channels lack NAC-assisted correction — BCH threshold irrelevant. The condition `lsmv2Decoder != null && p1Config.hasConfiguredNAC()` precisely targets simulcast channels that benefit.
**Alternatives Considered**: Global enable (harmful for C4FM), user-configurable (adds UI complexity). Auto-enable for target channels is the simplest correct approach.

## 2026-03-07 - Remove Quality Gate Auto-Enable; BCH T=5 Alone Is Optimal
**Context**: Windham PD (CQPSK_V2, NAC=1559) testing revealed quality gate is neutral-to-harmful on top of BCH T=5. Original +25% STT improvement was measured against V2 baseline (no BCH), not against BCH-only.
**Decision**: Remove `audioModule.setMaxImbeErrors(3)` auto-enable from `DecoderFactory`. Keep BCH T=5 auto-enabled. Quality gate remains available for manual configuration but is not the default.
**Rationale**: Windham PD STT results (Whisper, 2s segment gap):
- v2-baseline (BCH11, no gate): 31 words
- c5b0 (BCH5, no gate): **139 words (+348%)**
- c5b3 (BCH5, gate=3): 118 words (-15% vs BCH-only)
- c11b3 (BCH11, gate=3): 56 words (+81% vs baseline but far worse than BCH-only)
- adaptive (BCH5, gate=3, adaptive): 114 words (still worse than BCH-only)

ROC W cross-check confirms: BCH5-only (157 words) beats BCH5+gate3+adaptive (143 words, -9%).

Root cause: Windham PD has a non-bimodal error distribution (78% at 11+ errors, 13.2% at 0-3, 8.8% at 4-10). The quality gate's binary pass/fail is least effective for the intermediate 4-10 range. JMBE's partial decode of corrupted frames produces more useful speech than silence or frame repetition.

Frame repetition (replacing gated frames with last good frame) is also harmful — produces monotonous "you you you..." patterns that inflate STT word count without carrying information.
**Alternatives Considered**: (1) Adaptive gate auto-disable at <30% pass rate — partially mitigates but BCH-only remains superior. (2) Higher gate threshold (maxImbe=6) — reduces damage but still worse than no gate. (3) Keep gate enabled — rejected based on data showing harm across both bimodal (ROC W) and non-bimodal (Windham PD) channels when BCH T=5 is already active.
