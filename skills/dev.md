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

### 6. 验证
```bash
# 后端
cd backend && python -m pytest          # 运行测试
curl http://localhost:8000/health       # 服务健康检查

# 前端
./gradlew assembleDebug                 # 编译验证（无需安装）
```

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
