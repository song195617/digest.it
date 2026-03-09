# 发布流程

---

## APK 发布

### 本地构建
```bash
./build-apk.sh
```
- 使用 `digestit.jks` 固定签名（保证更新覆盖安装有效）
- 产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### 版本号修改
文件：`app/build.gradle.kts`
```kotlin
versionCode = X        // 整数，每次发布递增
versionName = "X.Y.Z"  // 语义化版本
```

### CI 自动构建
- **触发方式 1**：push tag `apk-*`（例如 `apk-v1.2.0`）
- **触发方式 2**：GitHub Actions 手动触发（workflow_dispatch）

### 发布前检查清单
- [ ] 版本号已递增（versionCode + versionName）
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] 在真机/模拟器上验证核心流程：提交URL → 处理 → 查看摘要
- [ ] `docs/features.md` 已更新

---

## 后端部署

### 启停服务
```bash
./start-backend.sh     # 启动 FastAPI + Celery + Redis + PostgreSQL
./stop-backend.sh      # 停止所有服务
```

### 服务健康检查
```bash
curl http://localhost:8000/health
# 预期响应：{"status": "ok", ...}
```

### 端口说明
| 服务 | 端口 |
|------|------|
| FastAPI | 8000 |
| Redis | 6379 |
| PostgreSQL | 5432 |

### 部署前检查清单
- [ ] `cd backend && python -m pytest` 测试通过
- [ ] 环境变量已配置（`.env` 文件）
- [ ] 数据库 migration 已执行（如有）
- [ ] `curl http://localhost:8000/health` 返回正常
