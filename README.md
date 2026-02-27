# digest.it

一款 Android 应用，支持将小宇宙播客和哔哩哔哩视频自动转录为文字，生成 AI 摘要，并提供基于内容的 AI 对话功能。

## 功能

- **链接提取**：支持小宇宙和哔哩哔哩链接，从分享菜单或手动粘贴
- **自动转录**：使用 OpenAI Whisper API 转录音频（自动分段处理长达 2 小时的内容）
- **B站字幕优先**：哔哩哔哩视频优先使用 AI 字幕，节省转录费用
- **AI 摘要**：使用 Claude API 生成结构化摘要（一句话概括、核心要点、精彩片段）
- **AI 对话**：基于转录内容的流式 AI 对话，支持时间戳引用
- **后台处理**：WorkManager 确保关闭 App 后继续处理，完成后推送通知

## 架构

```
Android App (Kotlin + Jetpack Compose + MVVM)
    ↕ HTTPS / WebSocket
Python Backend (FastAPI + Celery + PostgreSQL + Redis)
    ↕
外部 API: OpenAI Whisper | Claude API | Bilibili API | 小宇宙
```

## 快速开始

### 后端

```bash
cd backend
cp .env.example .env
# 编辑 .env 填写 API Key
docker-compose up -d
```

### Android

1. 用 Android Studio 打开 `app/` 目录
2. 首次启动 App 在设置页填写 API Key
3. 编译运行

## 费用估算

| 服务 | 费用 |
|------|------|
| OpenAI Whisper | $0.006/分钟（1小时约 $0.36）|
| Claude claude-sonnet-4-6 | 约 $0.01-0.05/期（摘要） |
| 哔哩哔哩有字幕视频 | 免费（跳过 Whisper）|

## 技术栈

**Android**: Kotlin, Jetpack Compose, MVVM, Hilt, Room, WorkManager, Retrofit, OkHttp

**Backend**: Python 3.12, FastAPI, Celery, PostgreSQL, Redis, yt-dlp, FFmpeg, OpenAI SDK, Anthropic SDK
