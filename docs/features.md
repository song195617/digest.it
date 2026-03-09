# 功能状态注册表

最后更新：2026-03-09

---

## 前端屏幕

| 屏幕 | 状态 | 关键文件 | 备注 |
|------|------|---------|------|
| HomeScreen | ✅ 🐛 | ui/home/HomeScreen.kt, HomeViewModel.kt | 刷新时偶发错误（EpisodeRepository）|
| ProcessingScreen | ✅ 🐛 | ui/processing/ProcessingScreen.kt | 失败后无返回/重试按钮 |
| SummaryScreen | ✅ | ui/summary/SummaryScreen.kt | 4个Tab：Overview/KeyPoints/Topics/Highlights |
| TranscriptScreen | ✅ 🐛 | ui/transcript/TranscriptScreen.kt | 时间戳 Chip 点击无效 |
| ChatScreen | ✅ 🐛 | ui/chat/ChatScreen.kt | 无清空聊天 UI 入口 |
| SettingsScreen | ✅ 📋 | ui/settings/SettingsScreen.kt | 缺少测试连接功能 |
| ShareActivity | ✅ | — | 系统分享意图处理 |

---

## 后端 API

| 端点 | 状态 | 文件 | 备注 |
|------|------|------|------|
| POST /v1/jobs | ✅ | api/v1/endpoints/jobs.py | 提交 URL，返回 job_id |
| GET /v1/jobs/{id} | ✅ | api/v1/endpoints/jobs.py | 轮询进度 |
| GET /v1/episodes | ✅ | api/v1/endpoints/episodes.py | 列出所有剧集 |
| GET /v1/episodes/{id} | ✅ | api/v1/endpoints/episodes.py | 剧集详情 |
| DELETE /v1/episodes/{id} | ✅ | api/v1/endpoints/episodes.py | 删除并 revoke Celery 任务 |
| POST /v1/episodes/{id}/retry | ✅ | api/v1/endpoints/episodes.py | 重新处理失败剧集 |
| GET /v1/episodes/{id}/transcript | ✅ | api/v1/endpoints/episodes.py | 获取转录文本 |
| GET /v1/episodes/{id}/summary | ✅ | api/v1/endpoints/episodes.py | 获取摘要 |
| WS /v1/ws/chat/{session_id} | ✅ | api/v1/endpoints/chat.py | 流式 AI 聊天 |
| GET /health | ✅ | api/v1/endpoints/health.py | 健康检查 |

---

## Celery 任务

| 任务 | 状态 | 文件 | 备注 |
|------|------|------|------|
| extract_task | ✅ 📋 | tasks/extract.py | Bilibili 字幕优先+音频兜底，小宇宙；缺自动重试 |
| transcribe_task | ✅ 📋 | tasks/transcribe.py | 缺自动重试机制 |
| summarize_task | ✅ 📋 | tasks/summarize.py | 缺自动重试机制 |

---

## 待办事项

### Sprint 1 — 高优先级

- 🐛 **[1.1] 首页刷新错误** → `EpisodeRepository.kt` + `HomeViewModel.kt`（低复杂度）
- 🐛 **[1.2] 处理失败无返回/重试按钮** → `ProcessingScreen.kt`（低复杂度）
- 📋 **[1.3] Settings 测试连接功能** → `SettingsScreen.kt` + 后端验证端点（中复杂度）
- 📋 **[1.4] Celery 任务自动重试** → `tasks/*.py`，配置 max_retries + countdown（低复杂度）

### Sprint 2 — 体验优化

- 🐛 **[2.1] 时间戳 Chip 点击跳转** → `TranscriptScreen.kt`（中复杂度）
- 📋 **[2.2] 摘要本地缓存** → Room DB 新增 summary 表（中复杂度）
- 📋 **[2.3] 分享/导出摘要** → SummaryScreen 分享 Intent（低复杂度）
- 📋 **[2.4] 离线状态横幅** → 全局网络状态监听（中复杂度）
- 📋 **[2.5] 下拉刷新** → HomeScreen SwipeRefresh（低复杂度）
- 🐛 **[2.6] 清空聊天 UI 入口** → `ChatScreen.kt`（低复杂度）

### Sprint 3 — 新能力

- 📋 **[3.1] 重新生成摘要** → 新 API 端点 + UI 按钮（高复杂度）
- 📋 **[3.2] 跨剧集全文搜索** → PostgreSQL FTS + 搜索 UI（高复杂度）
- 📋 **[3.3] YouTube 支持** → extractor 新平台适配（中复杂度）
- 📋 **[3.4] Apple Podcasts 支持** → extractor 新平台适配（中复杂度）
- 📋 **[3.5] Spotify 支持** → extractor 新平台适配（高复杂度）
- 📋 **[3.6] 深度分析摘要模式** → 新 prompt 模板 + UI 选项（中复杂度）
