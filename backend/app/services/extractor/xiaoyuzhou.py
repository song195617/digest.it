"""
Xiaoyuzhou (小宇宙) podcast audio extractor.

Strategy:
1. Try yt-dlp first (most reliable)
2. Fall back to scraping the Next.js JSON endpoint for audio URL on media.xyzcdn.net
"""
import re
import httpx
import yt_dlp
from pathlib import Path
from dataclasses import dataclass


@dataclass
class ExtractedAudio:
    audio_url: str
    title: str
    author: str
    cover_url: str | None
    duration_seconds: int
    local_path: str | None = None


async def extract_xiaoyuzhou(url: str, output_dir: str) -> ExtractedAudio:
    """Extract audio metadata and download from Xiaoyuzhou episode URL."""
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    # Try yt-dlp first
    try:
        return await _extract_with_ytdlp(url, output_dir)
    except Exception as e:
        pass

    # Fall back to web scraping
    return await _extract_by_scraping(url, output_dir)


async def _extract_with_ytdlp(url: str, output_dir: str) -> ExtractedAudio:
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": f"{output_dir}/%(id)s.%(ext)s",
        "quiet": True,
        "no_warnings": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        local_path = ydl.prepare_filename(info)
        return ExtractedAudio(
            audio_url=info.get("url", ""),
            title=info.get("title", "未知标题"),
            author=info.get("uploader", ""),
            cover_url=info.get("thumbnail"),
            duration_seconds=int(info.get("duration", 0)),
            local_path=local_path,
        )


async def _extract_by_scraping(url: str, output_dir: str) -> ExtractedAudio:
    """Scrape Xiaoyuzhou episode page to find audio URL on media.xyzcdn.net."""
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    async with httpx.AsyncClient(follow_redirects=True, timeout=30) as client:
        resp = await client.get(url, headers=headers)
        resp.raise_for_status()
        html = resp.text

    # Audio URL pattern from Next.js hydration data
    audio_pattern = re.compile(
        r'"mediaKey"\s*:\s*"([^"]+)".*?"enclosureUrl"\s*:\s*"(https://[^"]*media\.xyzcdn\.net[^"]*)"',
        re.DOTALL,
    )
    title_pattern = re.compile(r'"title"\s*:\s*"([^"]+)"')
    author_pattern = re.compile(r'"nickname"\s*:\s*"([^"]+)"')
    duration_pattern = re.compile(r'"duration"\s*:\s*(\d+)')
    cover_pattern = re.compile(r'"image"\s*:\s*\{[^}]*"picUrl"\s*:\s*"([^"]+)"')

    audio_match = audio_pattern.search(html)
    if not audio_match:
        # Simpler fallback pattern
        audio_url_simple = re.search(r'"enclosureUrl"\s*:\s*"(https://[^"]+)"', html)
        if not audio_url_simple:
            raise ValueError("Could not find audio URL in page HTML")
        audio_url = audio_url_simple.group(1)
    else:
        audio_url = audio_match.group(2)

    title = (title_pattern.search(html) or {1: "未知标题"}).group(1)
    author = (author_pattern.search(html) or {1: ""}).group(1)
    duration = int((duration_pattern.search(html) or {1: "0"}).group(1))
    cover = cover_pattern.search(html)
    cover_url = cover.group(1) if cover else None

    # Download audio
    local_path = await _download_audio(audio_url, output_dir)

    return ExtractedAudio(
        audio_url=audio_url,
        title=title,
        author=author,
        cover_url=cover_url,
        duration_seconds=duration,
        local_path=local_path,
    )


async def _download_audio(audio_url: str, output_dir: str) -> str:
    filename = audio_url.split("/")[-1].split("?")[0]
    local_path = f"{output_dir}/{filename}"
    async with httpx.AsyncClient(timeout=300, follow_redirects=True) as client:
        async with client.stream("GET", audio_url) as resp:
            resp.raise_for_status()
            with open(local_path, "wb") as f:
                async for chunk in resp.aiter_bytes(chunk_size=1024 * 1024):
                    f.write(chunk)
    return local_path
