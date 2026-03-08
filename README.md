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

**前置依赖**：Python 3.10+、PostgreSQL、Redis、FFmpeg

```bash
# 1. 首次部署：初始化环境、安装依赖、建库建表
bash setup-backend.sh

# 2. 编辑环境变量（填写 API Key 和数据库密码）
vi backend/.env

# 3. 启动所有服务
bash start-backend.sh

# 停止服务
bash stop-backend.sh
```

**GPU 加速**（可选）：如有 NVIDIA GPU，`setup-backend.sh` 会自动安装 CUDA 库，Whisper 转录将自动使用 GPU。同时建议在 `.env` 中将：
```
WHISPER_DEVICE=cuda
WHISPER_COMPUTE_TYPE=float16
```

### Android 本地编译

#### Windows 本机

```powershell
# 1. 一次性准备本地构建环境
powershell -ExecutionPolicy Bypass -File .\setup-android-build.ps1

# 2. 编译 debug APK
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1

# 3. 编译 release APK
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1 -Release
```

说明：
- `setup-android-build.ps1` 会优先复用 Android Studio 自带的 JBR 17，并把 `JAVA_HOME`、`ANDROID_HOME` 写入当前用户环境变量。
- `build-apk.ps1` 会先做安全检查，再执行 `:app:assembleDebug` 或 `:app:assembleRelease`。
- 编译成功后，可直接用脚本输出的 `adb install -r ...` 命令安装到手机。

#### WSL / Linux

```bash
bash setup-android-build.sh
bash build-apk.sh
```

### GitHub APK 下载流程

仓库内置了 GitHub Actions 工作流 [`android-apk.yml`](.github/workflows/android-apk.yml)：

- 推送到 `main` 后，会自动构建 debug APK 并上传为 Actions artifact。
- 手动触发 `workflow_dispatch` 时，可选择 `publish_release=true`，自动创建一个 prerelease 并附带 APK。
- 推送 `apk-*` 标签时，也会自动生成带 APK 的 prerelease，适合给手机直接下载安装测试。

建议测试流程：

1. 本地执行 `build-apk.ps1` 确认能编过。
2. 提交并推送到 GitHub。
3. 需要手机直接下载时，手动触发 Actions 并勾选 `publish_release`，或推一个 `apk-*` 标签。
4. 在 GitHub Release 页面下载 APK 到手机安装。

## 费用估算

| 服务 | 费用 |
|------|------|
| OpenAI Whisper | $0.006/分钟（1小时约 $0.36）|
| Claude claude-sonnet-4-6 | 约 $0.01-0.05/期（摘要） |
| 哔哩哔哩有字幕视频 | 免费（跳过 Whisper）|

## 技术栈

**Android**: Kotlin, Jetpack Compose, MVVM, Hilt, Room, WorkManager, Retrofit, OkHttp

**Backend**: Python 3.12, FastAPI, Celery, PostgreSQL, Redis, yt-dlp, FFmpeg, OpenAI SDK, Anthropic SDK
