#!/usr/bin/env bash
# stop-backend.sh — 停止所有后端服务
LOG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/backend/logs"

GREEN='\033[0;32m'; NC='\033[0m'
info() { echo -e "${GREEN}[stop]${NC} $*"; }

pkill -f "uvicorn app.main:app" 2>/dev/null && info "FastAPI stopped" || true
pkill -f "celery -A app.core.celery_app" 2>/dev/null && info "Celery stopped" || true
rm -f "$LOG_DIR"/*.pid

echo -e "${GREEN}✓ Done${NC}"
