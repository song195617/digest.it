import re
from dataclasses import dataclass
from app.models.episode import Platform


@dataclass
class ParsedUrl:
    platform: Platform
    normalized_url: str
    content_id: str | None


SHARE_URL = re.compile(r"(?:https?|bilibili|xiaoyuzhou)://[^\s]+")
BILIBILI_WEB = re.compile(r"bilibili\.com/video/(BV[\w]+)")
BILIBILI_SHORT = re.compile(r"b23\.tv/")
BILIBILI_AV = re.compile(r"bilibili\.com/video/av(\d+)")
XIAOYUZHOU_WEB = re.compile(r"xiaoyuzhoufm\.com/episode/([a-f0-9]+)")
XIAOYUZHOU_DEEP = re.compile(r"xiaoyuzhou://episode/([a-f0-9]+)")
TRAILING_PUNCTUATION = ".,;:!?)]}>'\"，。；：！？）】》」』、"


def extract_supported_url(raw_text: str) -> str | None:
    text = raw_text.strip()
    if not text:
        return None
    match = SHARE_URL.search(text)
    if not match:
        return None
    return match.group(0).rstrip(TRAILING_PUNCTUATION)


def detect_platform(url: str) -> ParsedUrl:
    original = url.strip()
    url = extract_supported_url(original) or original

    if m := BILIBILI_WEB.search(url):
        bvid = m.group(1)
        return ParsedUrl(Platform.BILIBILI, f"https://www.bilibili.com/video/{bvid}", bvid)

    if BILIBILI_SHORT.search(url):
        return ParsedUrl(Platform.BILIBILI, url, None)

    if m := BILIBILI_AV.search(url):
        aid = m.group(1)
        return ParsedUrl(Platform.BILIBILI, f"https://www.bilibili.com/video/av{aid}", aid)

    if m := XIAOYUZHOU_WEB.search(url):
        eid = m.group(1)
        return ParsedUrl(Platform.XIAOYUZHOU, url, eid)

    if m := XIAOYUZHOU_DEEP.search(url):
        eid = m.group(1)
        return ParsedUrl(
            Platform.XIAOYUZHOU,
            f"https://www.xiaoyuzhoufm.com/episode/{eid}",
            eid,
        )

    return ParsedUrl(Platform.UNKNOWN, url, None)
