"""
Bilibili (哔哩哔哩) content extractor.

Strategy:
1. Check for AI-generated subtitles via Bilibili player API (free, no transcription cost)
2. If no subtitles: extract audio stream via yt-dlp for Whisper transcription
"""
import json
import re
import httpx
import yt_dlp
from pathlib import Path
from dataclasses import dataclass


@dataclass
class BilibiliSubtitle:
    text: str
    language: str


@dataclass
class ExtractedContent:
    title: str
    author: str
    cover_url: str | None
    duration_seconds: int
    subtitle: BilibiliSubtitle | None = None  # If available, skip audio transcription
    audio_local_path: str | None = None


async def extract_bilibili(url: str, output_dir: str) -> ExtractedContent:
    """Extract content from Bilibili URL. Prefer subtitles over audio transcription."""
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    # Step 1: Get basic video info and check for subtitles
    video_info = await _get_video_info(url)

    # Step 2: Try to get AI-generated subtitle (saves transcription cost)
    subtitle = await _get_subtitle(video_info.get("bvid", ""), video_info.get("cid", ""))

    if subtitle:
        return ExtractedContent(
            title=video_info.get("title", ""),
            author=video_info.get("owner", {}).get("name", ""),
            cover_url=video_info.get("pic"),
            duration_seconds=video_info.get("duration", 0),
            subtitle=subtitle,
            audio_local_path=None,
        )

    # Step 3: No subtitle available - extract audio for Whisper transcription
    audio_path = await _extract_audio_ytdlp(url, output_dir)
    return ExtractedContent(
        title=video_info.get("title", ""),
        author=video_info.get("owner", {}).get("name", ""),
        cover_url=video_info.get("pic"),
        duration_seconds=video_info.get("duration", 0),
        subtitle=None,
        audio_local_path=audio_path,
    )


async def _get_video_info(url: str) -> dict:
    """Get Bilibili video metadata via public API."""
    bvid = _extract_bvid(url)
    if not bvid:
        return {}

    api_url = f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://www.bilibili.com",
    }
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get(api_url, headers=headers)
        data = resp.json()
    return data.get("data", {})


async def _get_subtitle(bvid: str, cid: str) -> BilibiliSubtitle | None:
    """
    Try to get AI-generated subtitle from Bilibili.
    Returns parsed subtitle text if available.
    """
    if not bvid or not cid:
        return None

    subtitle_api = f"https://api.bilibili.com/x/player/wbi/v2?bvid={bvid}&cid={cid}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": f"https://www.bilibili.com/video/{bvid}",
    }
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(subtitle_api, headers=headers)
            data = resp.json()

        subtitle_list = (
            data.get("data", {})
            .get("subtitle", {})
            .get("subtitles", [])
        )
        if not subtitle_list:
            return None

        # Prefer AI-generated Chinese subtitle
        ai_sub = next(
            (s for s in subtitle_list if "ai" in s.get("lan", "").lower()
             or "zh" in s.get("lan", "").lower()),
            subtitle_list[0] if subtitle_list else None,
        )
        if not ai_sub:
            return None

        subtitle_url = "https:" + ai_sub["subtitle_url"]
        async with httpx.AsyncClient(timeout=15) as client:
            sub_resp = await client.get(subtitle_url, headers=headers)
            sub_data = sub_resp.json()

        body = sub_data.get("body", [])
        full_text = "\n".join(item.get("content", "") for item in body)
        return BilibiliSubtitle(text=full_text, language=ai_sub.get("lan", "zh"))
    except Exception:
        return None


async def _extract_audio_ytdlp(url: str, output_dir: str) -> str:
    """Download audio stream from Bilibili using yt-dlp."""
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": f"{output_dir}/%(id)s.%(ext)s",
        "quiet": True,
        "no_warnings": True,
        "extractor_args": {"bilibili": {"try_look": ["1"]}},
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)


def _extract_bvid(url: str) -> str | None:
    m = re.search(r"(BV[\w]+)", url)
    return m.group(1) if m else None
