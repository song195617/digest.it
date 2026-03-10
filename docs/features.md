# 功能状态注册表

最后更新：2026-03-10（转录页段落化展示与句级跟播高亮）

---

## 前端屏幕

| 屏幕 | 状态 | 关键文件 | 备注 |
|------|------|---------|------|
| HomeScreen | ✅ 🐛 | ui/home/HomeScreen.kt, HomeViewModel.kt | 刷新时偶发错误（EpisodeRepository）；删除改为左滑露出按钮 + 二次确认；B站卡片可回填标题/封面 |
| ProcessingScreen | ✅ 🐛 | ui/processing/ProcessingScreen.kt | 失败后无返回/重试按钮 |
| SummaryScreen | ✅ | ui/summary/SummaryScreen.kt | 详细摘要 Tab 支持 Markdown 渲染 |
| TranscriptScreen | ✅ | ui/transcript/TranscriptScreen.kt | 段落化展示转录；句级跟播高亮；播放器已提升为全局底部播放栏 |
| ChatScreen | ✅ 🐛 | ui/chat/ChatScreen.kt | 无清空聊天 UI 入口 |
| SettingsScreen | ✅ | ui/settings/SettingsScreen.kt | 支持后端连接测试 + 音频缓存大小查看/清理，不显示全局播放器 |
| ShareActivity | ✅ | ui/share/ | 系统分享意图处理；支持从分享文案中自动提取链接 |

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
| GET /v1/episodes/{id}/summary | ✅ | api/v1/endpoints/episodes.py | 获取摘要；highlights 时间戳已修复（带时间戳转录） |
| GET /v1/episodes/{id}/audio | ✅ | api/v1/endpoints/episodes.py | 支持 Range 请求的音频流；EpisodeResponse 含 audio_url |
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
- 📋 **[1.4] Celery 任务自动重试** → `tasks/*.py`，配置 max_retries + countdown（低复杂度）

### Sprint 2 — 体验优化

- ✅ **[2.1] 时间戳 Chip 点击跳转 + 音频 seek** → `TranscriptScreen.kt` + ExoPlayer AudioPlayerBar（v1.3.5 修复后端 `audio_url` 路径错误，并兼容旧 `/api/v1` 音频地址；v1.3.6+ 补上跨 episode 时间点点击强制切源；转录页现按段展示并按句跟播高亮）
- ✅ **[2.x] 音频 Range seek 优化** → `episodes.py` 支持 HTTP Range，拖动到远处不再退化为整段顺序读取
- ✅ **[2.x] 音频本地缓存** → Media3 `SimpleCache` + SettingsScreen 缓存占用显示，最大 1GB
- ✅ **[2.x] 全局底部播放器** → `AppNavigation.kt` 全局承载播放栏，摘要/聊天/转录外也可控播放
- 📋 **[2.2] 摘要本地缓存** → Room DB 新增 summary 表（中复杂度）
- ✅ **[2.3] 分享/导出摘要** → SummaryScreen 分享 Intent（已完成）
- ✅ **[2.x] 摘要 Markdown 渲染** → SummaryScreen FullSummaryContent，mikepenz 渲染库
- ✅ **[2.x] 精彩片段时间戳修复** → prompts.py + claude/openai_service 使用带时间戳转录
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
