#!/usr/bin/env bash
set -euo pipefail

# Decode Quality Scoring Tool
# Runs decode comparison between control and test source trees, generates comparison report.
#
# Usage:
#   ./tools/score-decode-quality.sh \
#     --control <dir>           # Control source tree (unmodified)
#     --test <dir>              # Test source tree (proposed changes)
#     --samples <dir>           # Directory containing baseband .wav files
#     --playlist <xml>          # Playlist XML for channel config resolution
#     [--control-ref <commit>]  # Build control from commit/branch (creates worktree)
#     [--mode quick|full]       # quick=LDU only, full=audio analysis (default: quick)
#     [--jmbe <jar>]            # Path to JMBE codec jar (required for full mode)
#     [--output <dir>]          # Output directory (default: /tmp/decode-quality-TIMESTAMP)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"

# Defaults
CONTROL_DIR=""
TEST_DIR=""
CONTROL_REF=""
SAMPLES_DIR=""
PLAYLIST=""
MODE="quick"
JMBE_JAR=""
OUTPUT_DIR=""
WORKTREE_CREATED=""

usage() {
    echo "Usage: $0 --control <dir> --test <dir> --samples <dir> --playlist <xml> [options]"
    echo ""
    echo "Options:"
    echo "  --control <dir>           Control source tree (or use --control-ref)"
    echo "  --control-ref <ref>       Git ref to use as control (creates worktree)"
    echo "  --test <dir>              Test source tree (default: current repo)"
    echo "  --samples <dir>           Directory containing baseband .wav files"
    echo "  --playlist <xml>          Playlist XML for channel config"
    echo "  --mode quick|full         Scoring mode (default: quick)"
    echo "  --jmbe <jar>              JMBE codec jar (required for full mode)"
    echo "  --output <dir>            Output directory"
    exit 1
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --control)     CONTROL_DIR="$2"; shift 2 ;;
        --control-ref) CONTROL_REF="$2"; shift 2 ;;
        --test)        TEST_DIR="$2"; shift 2 ;;
        --samples)     SAMPLES_DIR="$2"; shift 2 ;;
        --playlist)    PLAYLIST="$2"; shift 2 ;;
        --mode)        MODE="$2"; shift 2 ;;
        --jmbe)        JMBE_JAR="$2"; shift 2 ;;
        --output)      OUTPUT_DIR="$2"; shift 2 ;;
        -h|--help)     usage ;;
        *)             echo "Unknown option: $1"; usage ;;
    esac
done

# Validate required args
if [[ -z "$SAMPLES_DIR" ]]; then echo "ERROR: --samples is required"; usage; fi
if [[ -z "$PLAYLIST" ]]; then echo "ERROR: --playlist is required"; usage; fi
if [[ -z "$CONTROL_DIR" && -z "$CONTROL_REF" ]]; then echo "ERROR: --control or --control-ref is required"; usage; fi
if [[ -z "$TEST_DIR" ]]; then TEST_DIR="$REPO_DIR"; fi

# Setup output directory
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="/tmp/decode-quality-$(date +%Y%m%d_%H%M%S)"
fi
mkdir -p "$OUTPUT_DIR"

CONTROL_OUTPUT="$OUTPUT_DIR/control"
TEST_OUTPUT="$OUTPUT_DIR/test"
mkdir -p "$CONTROL_OUTPUT" "$TEST_OUTPUT"

echo "╔══════════════════════════════════════════════════════╗"
echo "║         Decode Quality Scoring Tool                  ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "Mode:     $MODE"
echo "Samples:  $SAMPLES_DIR"
echo "Playlist: $PLAYLIST"
echo "Output:   $OUTPUT_DIR"

# Handle --control-ref: create worktree
if [[ -n "$CONTROL_REF" ]]; then
    WORKTREE_PATH="/tmp/sdrtrunk-control-$(echo "$CONTROL_REF" | tr '/' '-')"
    echo ""
    echo "Creating worktree for control at: $WORKTREE_PATH (ref: $CONTROL_REF)"

    if [[ -d "$WORKTREE_PATH" ]]; then
        echo "  Removing existing worktree..."
        git -C "$REPO_DIR" worktree remove "$WORKTREE_PATH" --force 2>/dev/null || rm -rf "$WORKTREE_PATH"
    fi

    git -C "$REPO_DIR" worktree add "$WORKTREE_PATH" "$CONTROL_REF"
    CONTROL_DIR="$WORKTREE_PATH"
    WORKTREE_CREATED="$WORKTREE_PATH"
    echo "  Worktree created at: $CONTROL_DIR"
fi

echo ""
echo "Control:  $CONTROL_DIR"
echo "Test:     $TEST_DIR"

cleanup() {
    if [[ -n "$WORKTREE_CREATED" && -d "$WORKTREE_CREATED" ]]; then
        echo ""
        echo "Cleaning up worktree: $WORKTREE_CREATED"
        git -C "$REPO_DIR" worktree remove "$WORKTREE_CREATED" --force 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Build and run decode for a given source tree
run_decode() {
    local src_dir="$1"
    local out_dir="$2"
    local label="$3"

    echo ""
    echo "━━━ Building $label ━━━"

    # Build test classes
    (cd "$src_dir" && ./gradlew compileTestJava 2>&1 | tail -5)

    echo "━━━ Running $label decode ━━━"

    local gradle_args="-Psamples=$SAMPLES_DIR -Pplaylist=$PLAYLIST -Poutput=$out_dir -Pmode=$MODE"
    if [[ -n "$JMBE_JAR" ]]; then
        gradle_args="$gradle_args -Pjmbe=$JMBE_JAR"
    fi

    (cd "$src_dir" && ./gradlew runDecodeScore $gradle_args 2>&1)
}

# Run control decode
run_decode "$CONTROL_DIR" "$CONTROL_OUTPUT" "CONTROL"

# Run test decode
run_decode "$TEST_DIR" "$TEST_OUTPUT" "TEST"

# Generate comparison report
echo ""
echo "━━━ Generating Comparison Report ━━━"

CONTROL_METRICS="$CONTROL_OUTPUT/metrics.json"
TEST_METRICS="$TEST_OUTPUT/metrics.json"

if [[ ! -f "$CONTROL_METRICS" ]]; then
    echo "ERROR: Control metrics not found at $CONTROL_METRICS"
    exit 1
fi

if [[ ! -f "$TEST_METRICS" ]]; then
    echo "ERROR: Test metrics not found at $TEST_METRICS"
    exit 1
fi

# Run Python comparison report
if [[ "$MODE" == "full" ]]; then
    python3 "$SCRIPT_DIR/audio_scorer.py" \
        --control-metrics "$CONTROL_METRICS" \
        --test-metrics "$TEST_METRICS" \
        --control-audio "$CONTROL_OUTPUT" \
        --test-audio "$TEST_OUTPUT" \
        --output "$OUTPUT_DIR/report.txt"
else
    python3 "$SCRIPT_DIR/audio_scorer.py" \
        --control-metrics "$CONTROL_METRICS" \
        --test-metrics "$TEST_METRICS" \
        --output "$OUTPUT_DIR/report.txt"
fi

echo ""
echo "Report: $OUTPUT_DIR/report.txt"
echo ""
cat "$OUTPUT_DIR/report.txt"
