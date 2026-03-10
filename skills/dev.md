# 开发 SOP

**触发时机：** 开始任何功能开发或 Bug 修复前。

---

## 步骤

### 1. 加载项目上下文
读取 `AGENTS.md` 了解项目全貌（对话中已加载则跳过）。

### 2. 了解当前功能状态
读取 `docs/features.md`，确认：
- 要开发/修复的项目当前状态
- 相关联的已有功能（避免重复实现）
- 当前 Sprint 优先级

### 3. 精准定位相关文件
使用 Glob/Grep 定位，**禁止全量扫描代码库**：
```
# 示例：定位某屏幕文件
Glob: app/src/main/java/com/digestit/ui/home/**
# 示例：查找某 API 端点
Grep: "episodes/{id}/retry" in backend/
```
只读必要文件，不读无关文件。

### 4. 制定方案（Plan Mode）
在实施前，用 Plan Mode 设计方案：
- 说明将修改哪些文件、做什么改动
- 说明潜在的副作用
- 与用户确认方案后再开始实施

### 5. 实施
按确认的方案执行，保持最小改动原则：
- 不引入计划外的修改
- 不重构无关代码
- 不添加多余注释或类型注解

### 5.1 跨端契约检查（前端依赖后端返回字段时必做）
- 若 Android 直接消费后端返回的相对路径或 URL（如 `audio_url`、图片、文件下载地址），必须同时核对：
  - 后端实际路由前缀是否与返回值一致（例如 `/v1/...` vs `/api/v1/...`）
  - 前端是否对历史字段值或旧缓存做兼容
- 发现路径契约问题时，优先：
  - 后端修正返回值
  - 前端增加兼容层，避免旧数据或旧服务端返回导致功能继续失效
- 修复后必须补回归测试，至少覆盖：
  - 后端字段值正确
  - 前端消费链路不会因旧值失效

### 5.2 音频播放类问题检查单
- 若问题涉及时间点跳转、转录页播放器、后台播放，必须同时检查：
  - 播放器 controller / media item / ready 状态
  - 后端音频 URL 是否可访问
  - 点击时间点后是否真正开始播放，而不是只进入 loading
- 不要只验证 seek 逻辑；要先确认音频源地址和播放器生命周期都成立。

### 6. 验证
```bash
# 后端
cd backend && python -m pytest          # 优先使用 pytest
cd backend && .venv/bin/python -m unittest tests.test_episodes_endpoint -v   # 若仓库自带 .venv 且未安装 pytest，可先跑 endpoint 回归
curl http://localhost:8000/health       # 服务健康检查

# 前端
./gradlew testDebugUnitTest assembleDebug   # 单测 + 编译验证
```

### 6.1 音频/时间点回归（涉及播放器改动时必做）
- `TranscriptScreen`：点击时间点后，底部播放器应在 1-2 秒内开始播放，进度条不应卡在 `0`
- `SummaryScreen`：点击 highlights 时间点，应跳转并从对应位置播放
- `ChatScreen`：点击时间戳 chip，应跳转并从对应位置播放
- 播放器基础能力：播放/暂停、拖动进度条、前进/后退 15 秒都要回归
- 若后端返回媒体路径，补后端测试覆盖：
  - 列表/详情接口返回值正确
  - 媒体流端点可返回文件响应

### 7. 提交
```
格式：<type>: <描述>
type ∈ {feat, fix, refactor, build, docs, test, chore}

示例：
  feat: 添加处理失败重试按钮
  fix: 修复首页刷新时 NullPointerException
```

### 8. 回刷文档（必须）
执行 `/sync-context`，或按照 `skills/sync-context.md` 步骤手动操作。

---

## 分支规范

| 类型 | 格式 | 示例 |
|------|------|------|
| 新功能 | `feat/xxx` | `feat/retry-button` |
| Bug修复 | `fix/xxx` | `fix/home-refresh-crash` |
