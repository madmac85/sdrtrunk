# Implementation Decisions: Missed Transmission Analysis

## 2026-01-31 - Replace Old MissedTransmissionAnalyzer API

**Context**: Existing MissedTransmissionAnalyzer used inner classes (EnergyProfile, MissedTransmission) for energy-based analysis. New spec requires failure categorization and signal quality correlation.

**Decision**: Completely rewrote MissedTransmissionAnalyzer to use TransmissionMapper for signal detection and added failure categorization.

**Rationale**:
- TransmissionMapper already provides robust signal-based transmission detection used by TransmissionDetectionAnalyzer
- New approach integrates with existing TransmissionDecodeResult infrastructure
- Failure categorization requires per-transmission analysis, not raw energy profiles
- Maintains consistency with 006-transmission-detection-metrics implementation

**Alternatives Considered**:
- Extend old MissedTransmissionAnalyzer - would duplicate TransmissionMapper logic
- Add new classes alongside old - would cause confusion

## 2026-01-31 - Failure Category Enum Design

**Context**: Need to categorize missed transmissions by failure cause.

**Decision**: Created FailureCategory enum with 5 categories: TOO_SHORT, WEAK_SIGNAL, SYNC_FAILURE, INCOMPLETE, UNKNOWN.

**Rationale**:
- TOO_SHORT (<180ms): Not a decoder failure - physically impossible to decode one LDU
- WEAK_SIGNAL: Bottom 10% of observed energy - signal too weak
- SYNC_FAILURE: Adequate signal but no sync - this is the optimization opportunity
- INCOMPLETE: Recording boundary cut-off - separate from true failures
- UNKNOWN: Fallback for edge cases requiring manual inspection

**Key insight**: Only SYNC_FAILURE and UNKNOWN are true optimization opportunities. Others are inherently undecodable.

## 2026-01-31 - Weak Signal Threshold Calculation

**Context**: Need to determine "weak signal" threshold.

**Decision**: Calculate threshold as bottom 10th percentile of observed energy levels.

**Rationale**:
- Adapts to actual signal conditions in each recording set
- Avoids arbitrary fixed threshold that may not match real conditions
- 10% matches spec default; could be made configurable

**Alternatives Considered**:
- Fixed absolute threshold - doesn't adapt to different recordings
- User-specified threshold - requires prior knowledge of signal levels

## 2026-01-31 - Signal Quality Binning Strategy

**Context**: Need to correlate signal quality with decode success.

**Decision**: Create 10 bins covering observed energy range, calculate decode rates per bin.

**Rationale**:
- 10 bins provides enough granularity without over-splitting sparse data
- Bins adapt to actual energy range in recordings
- Enables identification of "optimization zone" - signal range with most missed transmissions

## 2026-01-31 - Low Decode Rate Threshold

**Context**: Need to identify partial decode failures (not missed, but poor).

**Decision**: Default threshold of 20% decode rate (configurable constant).

**Rationale**:
- 20% means <1 of 5 expected LDUs decoded
- Distinguishes partial failures from missed (0%) and successful (>50%)
- Matches spec FR-003 requirement

## 2026-01-31 - Updated AudioComparisonTest API

**Context**: Existing AudioComparisonTest used old MissedTransmissionAnalyzer inner classes.

**Decision**: Updated to use TransmissionMapper and inline timestamp matching instead of EnergyProfile.

**Rationale**:
- Maintains backward compatibility with test workflow
- Uses same signal detection approach as MissedTransmissionAnalyzer
- Removes dependency on deleted inner classes

## Analysis Results Summary (8-file test suite)

| Metric | Value |
|--------|-------|
| Total Transmissions | 230 |
| Total Missed | 31 (13.5%) |
| Sync Failures | 25 (80.6% of missed) |
| Weak Signal | 3 (9.7%) |
| Incomplete | 3 (9.7%) |
| Too Short | 0 |
| Optimization Opportunities | 25 (80.6% of missed) |
| Low Rate Transmissions | 87 |
| v2 Better in Low Rate | 65 (74.7%) |

**Key Finding**: 80.6% of missed transmissions are sync acquisition failures with adequate signal, representing clear optimization opportunities for the decoder.
