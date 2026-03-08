MAX_TRANSCRIPT_CHARS = 80000


def truncate_transcript(text: str) -> str:
    if len(text) <= MAX_TRANSCRIPT_CHARS:
        return text
    half = MAX_TRANSCRIPT_CHARS // 2
    return text[:half] + "\n\n[... 中间内容已省略 ...]\n\n" + text[-half:]


def format_timestamp_ms(ms: int) -> str:
    total_seconds = ms // 1000
    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    seconds = total_seconds % 60
    if hours > 0:
        return f"[{hours:02d}:{minutes:02d}:{seconds:02d}]"
    return f"[{minutes:02d}:{seconds:02d}]"


def format_transcript_with_timestamps(segments: list[dict]) -> str:
    lines = []
    for seg in segments:
        ms = seg.get("start_ms", 0)
        lines.append(f"{format_timestamp_ms(ms)} {seg.get('text', '')}")
    return "\n".join(lines)


def build_chat_context(full_text: str, segments: list[dict]) -> str:
    transcript_with_timestamps = format_transcript_with_timestamps(segments)
    if transcript_with_timestamps.strip():
        return truncate_transcript(transcript_with_timestamps)
    return truncate_transcript(full_text)
