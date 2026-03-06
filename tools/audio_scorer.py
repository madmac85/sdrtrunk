#!/usr/bin/env python3
"""
Audio Quality Scorer and Comparison Report Generator

Reads metrics JSON from control and test decode runs, optionally analyzes audio files,
and produces a text comparison report.

Quick mode: Compares LDU counts and basic decode metrics only.
Full mode: Also analyzes decoded audio files for duration, segments, tones, distortion, and STT.

Usage:
    python3 audio_scorer.py \
        --control-metrics path/to/control/metrics.json \
        --test-metrics path/to/test/metrics.json \
        [--control-audio path/to/control/audio] \
        [--test-audio path/to/test/audio] \
        [--output path/to/report.txt]
"""

import argparse
import json
import os
import sys
import glob as glob_mod
from pathlib import Path

# Optional imports for full audio analysis
try:
    import numpy as np
    HAS_NUMPY = True
except ImportError:
    HAS_NUMPY = False

try:
    from scipy.io import wavfile
    from scipy import signal as scipy_signal
    HAS_SCIPY = True
except ImportError:
    HAS_SCIPY = False

try:
    from pydub import AudioSegment
    HAS_PYDUB = True
except ImportError:
    HAS_PYDUB = False


def load_metrics(path):
    """Load metrics JSON file."""
    with open(path, 'r') as f:
        return json.load(f)


def get_mp3_duration(mp3_path):
    """Get duration of an MP3 file in seconds."""
    if HAS_PYDUB:
        try:
            audio = AudioSegment.from_mp3(mp3_path)
            return len(audio) / 1000.0
        except Exception:
            pass

    # Fallback: estimate from file size (16kbps CBR MP3 = 2000 bytes/sec)
    try:
        size = os.path.getsize(mp3_path)
        return size / 2000.0
    except Exception:
        return 0.0


def analyze_audio_directory(audio_dir, enable_stt=False, stt_model="tiny"):
    """Analyze all MP3 audio segments in a directory."""
    results = {}

    # Find all per-file subdirectories
    if not os.path.isdir(audio_dir):
        return results

    subdirs = sorted([d for d in os.listdir(audio_dir) if os.path.isdir(os.path.join(audio_dir, d))])
    total_dirs = len(subdirs)

    # Pre-load whisper model once if STT enabled
    stt_model_obj = None
    if enable_stt:
        try:
            import whisper
            print(f"  Loading whisper model '{stt_model}'...", file=sys.stderr)
            stt_model_obj = whisper.load_model(stt_model)
            print(f"  Whisper model loaded.", file=sys.stderr)
        except ImportError:
            print("  WARNING: whisper not available, skipping STT", file=sys.stderr)

    for idx, subdir in enumerate(subdirs):
        subdir_path = os.path.join(audio_dir, subdir)

        mp3_files = sorted(glob_mod.glob(os.path.join(subdir_path, "segment_*.mp3")))
        if not mp3_files:
            continue

        print(f"  [{idx+1}/{total_dirs}] {subdir} ({len(mp3_files)} segments)...", file=sys.stderr)

        total_duration = 0.0
        segment_durations = []

        for mp3 in mp3_files:
            dur = get_mp3_duration(mp3)
            total_duration += dur
            segment_durations.append(dur)

        avg_duration = total_duration / len(segment_durations) if segment_durations else 0.0

        audio_metrics = {
            'segment_count': len(mp3_files),
            'total_seconds': total_duration,
            'avg_segment_seconds': avg_duration,
            'segment_durations': segment_durations,
        }

        # Tone and distortion detection (requires numpy + pydub)
        if HAS_NUMPY and HAS_PYDUB:
            tone_count, distortion_count = detect_tones_and_distortion(mp3_files)
            audio_metrics['tone_count'] = tone_count
            audio_metrics['distortion_count'] = distortion_count

        # Speech-to-text word count (only if enabled and model loaded)
        if stt_model_obj is not None:
            word_count = _run_stt_with_model(stt_model_obj, mp3_files)
            audio_metrics['word_count'] = word_count

        results[subdir] = audio_metrics

    return results


def _run_stt_with_model(model, mp3_files):
    """Run STT with a pre-loaded whisper model."""
    total_words = 0
    for mp3_path in mp3_files:
        try:
            result = model.transcribe(mp3_path, language="en")
            text = result.get("text", "").strip()
            if text:
                total_words += len(text.split())
        except Exception:
            continue
    return total_words


def detect_tones_and_distortion(mp3_files):
    """
    Detect dispatch tones and distortion events in audio segments.

    Dispatch tones: Sustained single-frequency signals (300-1200 Hz) lasting >1 second.
    Distortion: Spectral anomalies (broadband energy spikes) or silence gaps.
    """
    tone_count = 0
    distortion_count = 0

    if not HAS_PYDUB or not HAS_NUMPY:
        return tone_count, distortion_count

    for mp3_path in mp3_files:
        try:
            audio = AudioSegment.from_mp3(mp3_path)
            samples = np.array(audio.get_array_of_samples(), dtype=np.float32)
            sample_rate = audio.frame_rate

            if len(samples) < sample_rate:
                continue

            # Analyze in 0.5s windows
            window_size = sample_rate // 2
            sustained_tone_windows = 0
            last_peak_freq = None

            for i in range(0, len(samples) - window_size, window_size):
                window = samples[i:i + window_size]

                # Check for perfect silence (distortion indicator)
                if np.max(np.abs(window)) < 1.0:
                    distortion_count += 1
                    continue

                # FFT analysis
                fft = np.fft.rfft(window)
                magnitudes = np.abs(fft)
                freqs = np.fft.rfftfreq(len(window), 1.0 / sample_rate)

                # Find dominant frequency
                tone_mask = (freqs >= 300) & (freqs <= 1200)
                if not np.any(tone_mask):
                    continue

                tone_magnitudes = magnitudes[tone_mask]
                tone_freqs = freqs[tone_mask]

                peak_idx = np.argmax(tone_magnitudes)
                peak_freq = tone_freqs[peak_idx]
                peak_mag = tone_magnitudes[peak_idx]

                # Check if dominant frequency is a clear tone (high peak-to-average ratio)
                avg_mag = np.mean(tone_magnitudes)
                if avg_mag > 0 and peak_mag / avg_mag > 8.0:
                    if last_peak_freq is not None and abs(peak_freq - last_peak_freq) < 20:
                        sustained_tone_windows += 1
                    else:
                        sustained_tone_windows = 1
                    last_peak_freq = peak_freq
                else:
                    # Check for distortion: broadband energy spike
                    high_freq_mask = freqs > 3000
                    if np.any(high_freq_mask):
                        high_energy = np.mean(magnitudes[high_freq_mask])
                        low_energy = np.mean(magnitudes[tone_mask])
                        if low_energy > 0 and high_energy / low_energy > 0.5:
                            distortion_count += 1

                    if sustained_tone_windows >= 2:  # 2 windows of 0.5s = 1 second
                        tone_count += 1
                    sustained_tone_windows = 0
                    last_peak_freq = None

            # Check if file ended during a sustained tone
            if sustained_tone_windows >= 2:
                tone_count += 1

        except Exception:
            continue

    return tone_count, distortion_count


def run_stt(mp3_files, model_size="tiny"):
    """Run speech-to-text on audio files and return total word count."""
    try:
        import whisper
        model = whisper.load_model(model_size)
    except ImportError:
        try:
            from faster_whisper import WhisperModel
            model = WhisperModel("base", compute_type="int8")
            return _run_stt_faster_whisper(model, mp3_files)
        except ImportError:
            return None

    total_words = 0
    for mp3_path in mp3_files:
        try:
            result = model.transcribe(mp3_path, language="en")
            text = result.get("text", "").strip()
            if text:
                total_words += len(text.split())
        except Exception:
            continue

    return total_words


def _run_stt_faster_whisper(model, mp3_files):
    """Run STT using faster-whisper."""
    total_words = 0
    for mp3_path in mp3_files:
        try:
            segments, _ = model.transcribe(mp3_path, language="en")
            for segment in segments:
                text = segment.text.strip()
                if text:
                    total_words += len(text.split())
        except Exception:
            continue
    return total_words


def format_delta(control_val, test_val, higher_is_better=True, fmt=".0f"):
    """Format a comparison value with arrow indicator."""
    if control_val == 0 and test_val == 0:
        return "0"

    delta = test_val - control_val
    if delta == 0:
        return f"{test_val:{fmt}}"

    arrow = ""
    if higher_is_better:
        arrow = " \u2191" if delta > 0 else " \u2193"
    else:
        arrow = " \u2193" if delta < 0 else " \u2191"  # Lower is better, so down is good

    pct = ""
    if control_val != 0:
        pct_val = (delta / control_val) * 100
        pct = f" ({pct_val:+.1f}%)"

    return f"{test_val:{fmt}}{arrow}{pct}"


def generate_report(control_metrics, test_metrics, control_audio=None, test_audio=None):
    """Generate comparison report text."""
    lines = []
    lines.append("=" * 80)
    lines.append("DECODE QUALITY COMPARISON REPORT")
    lines.append("=" * 80)
    lines.append("")

    # Build lookup by file
    control_by_file = {m['file']: m for m in control_metrics}
    test_by_file = {m['file']: m for m in test_metrics}

    all_files = sorted(set(list(control_by_file.keys()) + list(test_by_file.keys())))

    if not all_files:
        lines.append("No files to compare.")
        return "\n".join(lines)

    # Aggregate stats
    total_control_ldu = 0
    total_test_ldu = 0
    total_control_valid = 0
    total_test_valid = 0
    total_control_sync_blocked = 0
    total_test_sync_blocked = 0

    # Per-modulation aggregates
    mod_stats = {}

    for f in all_files:
        c = control_by_file.get(f, {})
        t = test_by_file.get(f, {})

        channel = c.get('channel', t.get('channel', 'Unknown'))
        mod = c.get('modulation', t.get('modulation', '?'))
        tuner = c.get('tuner', t.get('tuner', 'N/A'))
        is_fd = c.get('is_fd', t.get('is_fd', False))

        c_ldu = c.get('ldu_count', 0)
        t_ldu = t.get('ldu_count', 0)
        c_valid = c.get('valid_messages', 0)
        t_valid = t.get('valid_messages', 0)
        c_blocked = c.get('sync_blocked', 0)
        t_blocked = t.get('sync_blocked', 0)

        total_control_ldu += c_ldu
        total_test_ldu += t_ldu
        total_control_valid += c_valid
        total_test_valid += t_valid
        total_control_sync_blocked += c_blocked
        total_test_sync_blocked += t_blocked

        # Per-modulation
        if mod not in mod_stats:
            mod_stats[mod] = {'c_ldu': 0, 't_ldu': 0, 'c_valid': 0, 't_valid': 0, 'files': 0}
        mod_stats[mod]['c_ldu'] += c_ldu
        mod_stats[mod]['t_ldu'] += t_ldu
        mod_stats[mod]['c_valid'] += c_valid
        mod_stats[mod]['t_valid'] += t_valid
        mod_stats[mod]['files'] += 1

        ldu_delta = t_ldu - c_ldu
        arrow = "\u2191" if ldu_delta > 0 else ("\u2193" if ldu_delta < 0 else "=")
        delta_str = f"{ldu_delta:+d} {arrow}"
        pct_str = f" ({(ldu_delta / c_ldu * 100):+.1f}%)" if c_ldu > 0 else ""

        fd_tag = " [FD]" if is_fd else ""

        lines.append(f"  {channel}{fd_tag}")
        lines.append(f"    File: {f}")
        lines.append(f"    Mod: {mod} | NAC: {c.get('nac', t.get('nac', 0))} | Tuner: {tuner}")
        lines.append(f"    LDU:   {c_ldu:>6} \u2192 {t_ldu:>6}  ({delta_str}{pct_str})")
        lines.append(f"    Valid: {c_valid:>6} \u2192 {t_valid:>6}")

        if c_blocked > 0 or t_blocked > 0:
            lines.append(f"    Sync Blocked: {c_blocked:>6} \u2192 {t_blocked:>6}")

        # Signal-to-decode ratio
        c_signal = c.get('signal_seconds', 0)
        t_signal = t.get('signal_seconds', 0)
        c_ratio = c.get('decode_ratio', 0)
        t_ratio = t.get('decode_ratio', 0)
        if c_signal > 0 or t_signal > 0:
            lines.append(f"    Signal Time: {c_signal:>6.1f}s \u2192 {t_signal:>6.1f}s | Decode: {c_ratio:.1f}% \u2192 {t_ratio:.1f}%")

        lines.append("")

    # Audio analysis (full mode)
    has_audio = control_audio is not None and test_audio is not None
    if has_audio:
        lines.append("")
        lines.append("=" * 80)
        lines.append("AUDIO QUALITY ANALYSIS")
        lines.append("=" * 80)
        lines.append("")

        for f in all_files:
            c = control_by_file.get(f, {})
            t = test_by_file.get(f, {})
            channel = c.get('channel', t.get('channel', 'Unknown'))
            is_fd = c.get('is_fd', t.get('is_fd', False))

            # Get file key for audio lookup
            file_key = f.replace(' ', '_').replace('_baseband.wav', '').replace('.', '_')
            # Try to find audio dirs
            c_audio = control_audio.get(file_key, control_audio.get(f, {})) if isinstance(control_audio, dict) else {}
            t_audio = test_audio.get(file_key, test_audio.get(f, {})) if isinstance(test_audio, dict) else {}

            if not c_audio and not t_audio:
                continue

            lines.append(f"--- {channel} ({f}) ---")

            c_secs = c_audio.get('total_seconds', c.get('audio_seconds', 0))
            t_secs = t_audio.get('total_seconds', t.get('audio_seconds', 0))
            c_segs = c_audio.get('segment_count', c.get('audio_segments', 0))
            t_segs = t_audio.get('segment_count', t.get('audio_segments', 0))
            c_avg = c_audio.get('avg_segment_seconds', c_secs / c_segs if c_segs > 0 else 0)
            t_avg = t_audio.get('avg_segment_seconds', t_secs / t_segs if t_segs > 0 else 0)

            lines.append(f"  Total Audio:     {format_delta(c_secs, t_secs, True, '.1f')}s  (control: {c_secs:.1f}s)")
            lines.append(f"  Segments:        {format_delta(c_segs, t_segs, False)}  (control: {c_segs})")
            lines.append(f"  Avg Seg Length:  {format_delta(c_avg, t_avg, True, '.1f')}s  (control: {c_avg:.1f}s)")

            if is_fd:
                c_tones = c_audio.get('tone_count', 0)
                t_tones = t_audio.get('tone_count', 0)
                lines.append(f"  Dispatch Tones:  {format_delta(c_tones, t_tones, True)}  (control: {c_tones})")

            c_dist = c_audio.get('distortion_count', 0)
            t_dist = t_audio.get('distortion_count', 0)
            lines.append(f"  Distortion:      {format_delta(c_dist, t_dist, False)}  (control: {c_dist})")

            c_words = c_audio.get('word_count')
            t_words = t_audio.get('word_count')
            if c_words is not None or t_words is not None:
                c_w = c_words or 0
                t_w = t_words or 0
                lines.append(f"  Words (STT):     {format_delta(c_w, t_w, True)}  (control: {c_w})")

            lines.append("")

    # Summary
    lines.append("")
    lines.append("=" * 80)
    lines.append("SUMMARY")
    lines.append("=" * 80)
    lines.append("")

    lines.append(f"  Files Compared:       {len(all_files)}")
    lines.append(f"  Total LDU Control:    {total_control_ldu}")
    lines.append(f"  Total LDU Test:       {total_test_ldu}")

    total_delta = total_test_ldu - total_control_ldu
    arrow = "\u2191" if total_delta > 0 else ("\u2193" if total_delta < 0 else "=")
    pct = f" ({(total_delta / total_control_ldu * 100):+.1f}%)" if total_control_ldu > 0 else ""
    lines.append(f"  LDU Delta:            {total_delta:+d} {arrow}{pct}")
    lines.append(f"  Total Valid Control:  {total_control_valid}")
    lines.append(f"  Total Valid Test:     {total_test_valid}")
    lines.append(f"  Sync Blocked Ctrl:    {total_control_sync_blocked}")
    lines.append(f"  Sync Blocked Test:    {total_test_sync_blocked}")
    lines.append("")

    # Per-modulation summary
    lines.append("  By Modulation:")
    for mod, stats in sorted(mod_stats.items()):
        delta = stats['t_ldu'] - stats['c_ldu']
        arrow = "\u2191" if delta > 0 else ("\u2193" if delta < 0 else "=")
        pct = f" ({(delta / stats['c_ldu'] * 100):+.1f}%)" if stats['c_ldu'] > 0 else ""
        lines.append(f"    {mod:<12} {stats['files']} files: {stats['c_ldu']} \u2192 {stats['t_ldu']} LDUs ({delta:+d} {arrow}{pct})")

    lines.append("")

    # Verdict
    if total_delta > 0:
        lines.append("  VERDICT: TEST IS BETTER (+LDUs)")
    elif total_delta < 0:
        lines.append("  VERDICT: CONTROL IS BETTER (test lost LDUs)")
    else:
        lines.append("  VERDICT: NO CHANGE (identical LDU counts)")

    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Decode Quality Comparison Report Generator")
    parser.add_argument("--control-metrics", required=True, help="Path to control metrics.json")
    parser.add_argument("--test-metrics", required=True, help="Path to test metrics.json")
    parser.add_argument("--control-audio", help="Path to control audio directory (full mode)")
    parser.add_argument("--test-audio", help="Path to test audio directory (full mode)")
    parser.add_argument("--output", help="Output report file path")
    parser.add_argument("--stt", action="store_true", help="Enable speech-to-text word counting (slow on CPU)")
    parser.add_argument("--stt-model", default="tiny", help="Whisper model size (default: tiny)")

    args = parser.parse_args()

    control_metrics = load_metrics(args.control_metrics)
    test_metrics = load_metrics(args.test_metrics)

    control_audio_analysis = None
    test_audio_analysis = None

    if args.control_audio and args.test_audio:
        print("Analyzing control audio...", file=sys.stderr)
        control_audio_analysis = analyze_audio_directory(args.control_audio, enable_stt=args.stt, stt_model=args.stt_model)
        print("Analyzing test audio...", file=sys.stderr)
        test_audio_analysis = analyze_audio_directory(args.test_audio, enable_stt=args.stt, stt_model=args.stt_model)

    report = generate_report(control_metrics, test_metrics, control_audio_analysis, test_audio_analysis)

    if args.output:
        with open(args.output, 'w') as f:
            f.write(report)
        print(f"Report written to: {args.output}", file=sys.stderr)
    else:
        print(report)


if __name__ == "__main__":
    main()
