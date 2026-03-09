# digest.it — AI 开发指南

digest.it 是一个播客/视频内容消化 App，支持提交 URL 后自动提取转录并生成 AI 摘要，提供多标签浏览和 AI 问答。前端为 Android（Kotlin/Jetpack Compose），后端为 FastAPI + Celery + PostgreSQL + Redis。

---

## 技术栈速查

| 层 | 技术 |
|----|------|
| Android 前端 | Kotlin、Jetpack Compose、Room、Retrofit、WorkManager、WebSocket |
| 后端 API | FastAPI（Python）、Uvicorn |
| 异步任务 | Celery + Redis broker |
| 数据库 | PostgreSQL（SQLAlchemy ORM）|
| AI 服务 | Claude / OpenAI（可切换）|
| 内容抓取 | Bilibili（字幕优先 + 音频兜底）、小宇宙 |

---

## 关键文件路径地图

```
digest.it/
├── app/src/main/java/com/digestit/
│   ├── ui/
│   │   ├── home/           # HomeScreen + HomeViewModel
│   │   ├── processing/     # ProcessingScreen + ProcessingViewModel
│   │   ├── summary/        # SummaryScreen（4个Tab）
│   │   ├── transcript/     # TranscriptScreen
│   │   ├── chat/           # ChatScreen（WebSocket）
│   │   └── settings/       # SettingsScreen
│   ├── data/
│   │   ├── repository/     # EpisodeRepository, JobRepository
│   │   ├── local/          # Room DB + DAO
│   │   └── remote/         # Retrofit API + DTOs
│   └── worker/             # JobPollingWorker
├── backend/app/
│   ├── api/v1/endpoints/   # FastAPI 路由（jobs, episodes, chat, health）
│   ├── tasks/              # Celery 任务（extract, transcribe, summarize）
│   ├── services/           # AI、extractor、transcription 服务
│   └── models/             # SQLAlchemy 模型
├── docs/
│   ├── features.md         # 功能状态注册表（📋/✅/🐛）
│   └── architecture.md     # 架构详细参考
├── skills/
│   ├── dev.md              # 开发 SOP
│   ├── release.md          # 发布流程
│   └── sync-context.md     # 文档回刷 SOP
└── .claude/commands/       # Claude Code 斜杠命令（/dev /release /sync-context）
```

---

## 已有功能清单

### 前端（7个屏幕）

| 屏幕 | 状态 | 关键文件 |
|------|------|---------|
| HomeScreen | ✅ | ui/home/ |
| ProcessingScreen | ✅ 🐛 | ui/processing/ |
| SummaryScreen | ✅ | ui/summary/ |
| TranscriptScreen | ✅ 🐛 | ui/transcript/ |
| ChatScreen | ✅ 🐛 | ui/chat/ |
| SettingsScreen | ✅ | ui/settings/ |
| ShareActivity | ✅ | — |

### 后端（10个端点）

| 端点 | 说明 |
|------|------|
| POST /v1/jobs | 提交 URL，返回 job_id |
| GET /v1/jobs/{id} | 轮询进度（pending/processing/done/failed）|
| GET /v1/episodes | 列出所有剧集 |
| GET /v1/episodes/{id} | 剧集详情 |
| DELETE /v1/episodes/{id} | 删除剧集（含 revoke Celery 任务）|
| POST /v1/episodes/{id}/retry | 重新处理失败剧集 |
| GET /v1/episodes/{id}/transcript | 获取转录文本 |
| GET /v1/episodes/{id}/summary | 获取摘要 |
| WS /v1/ws/chat/{session_id} | 流式 AI 聊天 |
| GET /health | 健康检查 |

### Celery 任务链

```
extract_task → transcribe_task → summarize_task
```

---

## 当前开发优先级

详见 [docs/features.md](./docs/features.md)。

**Sprint 1（高优先级）：**
- 🐛 首页刷新错误（EpisodeRepository.kt + HomeViewModel.kt）
- 🐛 处理失败后无返回/重试按钮（ProcessingScreen）
- 📋 Settings 测试连接功能
- 📋 Celery 任务自动重试

---

## 开发规范

- **提交格式**：`<type>: <描述>`，type ∈ {feat, fix, refactor, build, docs, test, chore}
- **分支命名**：`feat/xxx`、`fix/xxx`
- **禁止**全量扫描代码库；优先用 Glob/Grep 精准定位
- **每次开发完成后**必须执行 `/sync-context` 回刷文档

---

## 常用命令

```bash
# 后端
./start-backend.sh          # 启动 FastAPI + Celery + Redis + PostgreSQL
./stop-backend.sh           # 停止所有服务
curl http://localhost:8000/health  # 服务检查

# 前端 APK
./build-apk.sh              # 本地构建（使用 digestit.jks 固定签名）
./gradlew assembleDebug     # 调试构建
```

---

## 使用 AI 开发的 SOP

- **开发新功能或修复 Bug**：参见 [skills/dev.md](./skills/dev.md)（Claude Code: `/dev`）
- **发布 APK/后端**：参见 [skills/release.md](./skills/release.md)（Claude Code: `/release`）
- **同步文档**：参见 [skills/sync-context.md](./skills/sync-context.md)（Claude Code: `/sync-context`）
- **其他 AI 工具**（Cursor/Copilot/Codex）：直接将 `skills/` 下对应文件内容粘贴给 AI
