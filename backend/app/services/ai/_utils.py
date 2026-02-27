MAX_TRANSCRIPT_CHARS = 80000


def truncate_transcript(text: str) -> str:
    if len(text) <= MAX_TRANSCRIPT_CHARS:
        return text
    half = MAX_TRANSCRIPT_CHARS // 2
    return text[:half] + "\n\n[... 中间内容已省略 ...]\n\n" + text[-half:]


def format_transcript_with_timestamps(segments: list[dict]) -> str:
    lines = []
    for seg in segments:
        ms = seg.get("start_ms", 0)
        mins = ms // 60000
        secs = (ms % 60000) // 1000
        lines.append(f"[{mins:02d}:{secs:02d}] {seg.get('text', '')}")
    return "\n".join(lines)
