# Implementation Decisions: Transmission Detection Metrics

## 2026-01-31 - Extend Existing Infrastructure vs New Tool

**Context**: Needed to decide between creating standalone signal detection tool or extending existing TransmissionScoringTest.

**Decision**: Extended existing infrastructure with new classes that integrate with TransmissionScorer.

**Rationale**:
- TransmissionMapper already provides robust signal-based transmission detection
- TransmissionScoringTest already runs both decoders
- Reusing existing code minimizes duplication and ensures consistency
- New classes (DetectionMetrics, TransmissionDecodeResult) can be used by both existing test and new analyzer

**Alternatives Considered**:
- Standalone signal detector tool - would duplicate TransmissionMapper logic
- Modify TransmissionScore record - would break existing report formats

## 2026-01-31 - Transmission Decode Status Classification

**Context**: Needed to define categories for transmission decode outcomes.

**Decision**: Four-way classification: BOTH_DECODED, LSM_ONLY, V2_ONLY, MISSED

**Rationale**:
- Directly answers "which decoder recovered this transmission"
- MISSED category highlights optimization opportunities
- V2_ONLY category quantifies v2's unique value

**Alternatives Considered**:
- Binary classification (decoded/not decoded) - loses decoder comparison information
- Percentage thresholds - more complex without added value

## 2026-01-31 - "Decoded" Definition

**Context**: When is a transmission considered "decoded" by a decoder?

**Decision**: A transmission is "decoded" if at least 1 LDU was recovered within its time window.

**Rationale**:
- Simple, unambiguous threshold
- Any LDU recovery indicates successful sync acquisition
- Consistent with existing TransmissionScore logic

**Alternatives Considered**:
- Percentage threshold (e.g., >50% of expected) - arbitrary and varies by transmission length
- Require HDU/TDU - too strict, many valid decodes lack framing
