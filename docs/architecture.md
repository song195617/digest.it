# 架构参考

---

## 数据流

```
用户提交 URL
    ↓
POST /v1/jobs
    ↓
Celery: extract_task（提取音频/字幕）
    ↓
Celery: transcribe_task（语音转文字）
    ↓
Celery: summarize_task（AI 摘要生成）
    ↓
前端轮询 GET /v1/jobs/{id} 获取进度
    ↓
完成后跳转 SummaryScreen
```

---

## 关键目录结构

```
digest.it/
├── app/src/main/java/com/digestit/
│   ├── ui/
│   │   ├── home/           # HomeScreen, HomeViewModel
│   │   ├── processing/     # ProcessingScreen, ProcessingViewModel
│   │   ├── summary/        # SummaryScreen（Overview/KeyPoints/Topics/Highlights）
│   │   ├── transcript/     # TranscriptScreen（时间戳分段）
│   │   ├── chat/           # ChatScreen（WebSocket 流式对话）
│   │   └── settings/       # SettingsScreen（AI Provider 切换、API Key）
│   ├── data/
│   │   ├── repository/     # EpisodeRepository, JobRepository
│   │   ├── local/          # Room DB（EpisodeDao 等）
│   │   └── remote/         # Retrofit ApiService + DTOs
│   └── worker/
│       └── JobPollingWorker.kt   # WorkManager 后台轮询任务状态
│
├── backend/app/
│   ├── api/v1/endpoints/
│   │   ├── jobs.py         # POST /v1/jobs, GET /v1/jobs/{id}
│   │   ├── episodes.py     # CRUD + retry + transcript + summary
│   │   ├── chat.py         # WebSocket /v1/ws/chat/{session_id}
│   │   └── health.py       # GET /health
│   ├── tasks/
│   │   ├── extract.py      # extract_task
│   │   ├── transcribe.py   # transcribe_task
│   │   └── summarize.py    # summarize_task
│   ├── services/
│   │   ├── ai_service.py        # Claude / OpenAI 统一接口
│   │   ├── extractor.py         # Bilibili / 小宇宙 内容提取
│   │   └── transcription.py     # 语音转文字服务
│   └── models/
│       └── episode.py      # SQLAlchemy Episode 模型
│
├── backend/app/core/
│   ├── celery_app.py       # Celery 配置
│   └── config.py           # 环境变量配置
```

---

## 端口

| 服务 | 端口 |
|------|------|
| FastAPI | 8000 |
| Redis（Celery Broker）| 6379 |
| PostgreSQL | 5432 |

---

## Android 数据层说明

- **Room DB**：本地缓存 Episode 列表，离线可读
- **Retrofit**：与后端 REST API 通信
- **WorkManager**：`JobPollingWorker` 在后台轮询任务状态，App 进入后台后持续轮询
- **WebSocket**：ChatScreen 直连后端 `/v1/ws/chat/{session_id}`，流式接收 AI 回复

---

## AI Provider 切换机制

前端 SettingsScreen 可配置：
- Claude（默认）
- OpenAI
- 自定义 Base URL

配置加密存储于本地（Android EncryptedSharedPreferences），每次请求携带 API Key 发往后端，后端 `ai_service.py` 根据 Provider 类型选择对应 SDK。

---

## 内容平台支持

| 平台 | 提取策略 |
|------|---------|
| Bilibili | 字幕优先（CC字幕）→ 无字幕时下载音频转写 |
| 小宇宙 | 音频下载 → 转写 |
| YouTube | 📋 待支持 |
| Apple Podcasts | 📋 待支持 |
| Spotify | 📋 待支持 |
