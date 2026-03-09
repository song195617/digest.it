# Skills 使用说明

`skills/` 目录包含 digest.it 项目的标准操作规程（SOP），提交到 git，工具无关。

## 各 AI 工具的使用方式

### Claude Code
通过斜杠命令调用（已在 `.claude/commands/` 注册）：
- `/dev` — 开发 SOP（新功能/Bug修复）
- `/release` — 发布流程（APK + 后端）
- `/sync-context` — 开发完成后回刷文档

### Cursor / GitHub Copilot / OpenAI Codex
将对应 `skills/*.md` 文件的内容直接粘贴给 AI，或通过 `@` 引用文件：
- `@skills/dev.md` — 开始开发时引用
- `@skills/sync-context.md` — 完成开发后引用

### 手动操作
直接打开对应文件，按步骤操作即可。

## 文件清单

| 文件 | 用途 |
|------|------|
| `dev.md` | 开发 SOP：从分析到提交的完整流程 |
| `release.md` | 发布流程：APK 构建 + 后端部署 |
| `sync-context.md` | 文档回刷：每次开发完成后必须执行 |
