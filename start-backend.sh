#!/usr/bin/env bash
# start-backend.sh — 一键启动所有后端服务
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$SCRIPT_DIR/backend"
LOG_DIR="$SCRIPT_DIR/backend/logs"
mkdir -p "$LOG_DIR"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info() { echo -e "${GREEN}[start]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}  $*"; }

# ── 停止旧进程 ────────────────────────────────────────────────────────────────
stop_old() {
    pkill -f "uvicorn app.main:app" 2>/dev/null && info "Stopped old uvicorn" || true
    pkill -f "celery -A app.core.celery_app" 2>/dev/null && info "Stopped old celery" || true
    sleep 1
}

# ── PostgreSQL ────────────────────────────────────────────────────────────────
start_postgres() {
    if pg_isready -q 2>/dev/null; then
        info "PostgreSQL already running"
    else
        info "Starting PostgreSQL..."
        sudo service postgresql start
    fi
}

# ── Redis ─────────────────────────────────────────────────────────────────────
start_redis() {
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        info "Redis already running"
    else
        info "Starting Redis..."
        sudo service redis-server start
    fi
}

# ── CUDA LD_LIBRARY_PATH ──────────────────────────────────────────────────────
setup_cuda() {
    source "$BACKEND/.venv/bin/activate"
    export LD_LIBRARY_PATH=$(python -c "
import os
try:
    import nvidia.cublas.lib as c, nvidia.cudnn.lib as d
    print(os.path.dirname(c.__file__) + ':' + os.path.dirname(d.__file__))
except ImportError:
    print('')
" 2>/dev/null)
    if [[ -n "$LD_LIBRARY_PATH" ]]; then
        info "CUDA libraries: $LD_LIBRARY_PATH"
    else
        warn "nvidia-cublas/cudnn not found — Whisper will run on CPU"
    fi
}

# ── FastAPI ───────────────────────────────────────────────────────────────────
start_uvicorn() {
    info "Starting FastAPI (log: $LOG_DIR/uvicorn.log)..."
    cd "$BACKEND"
    nohup uvicorn app.main:app --host 0.0.0.0 --port 8000 \
        > "$LOG_DIR/uvicorn.log" 2>&1 &
    echo $! > "$LOG_DIR/uvicorn.pid"
    sleep 2
    if curl -sf http://localhost:8000/v1/episodes > /dev/null 2>&1; then
        info "FastAPI ready at http://localhost:8000"
    else
        warn "FastAPI may still be starting — check $LOG_DIR/uvicorn.log"
    fi
}

# ── Celery ────────────────────────────────────────────────────────────────────
start_celery() {
    info "Starting Celery worker (log: $LOG_DIR/celery.log)..."
    # cd into $BACKEND so Python can find the 'app' package
    cd "$BACKEND"
    nohup celery -A app.core.celery_app worker --loglevel=info \
        > "$LOG_DIR/celery.log" 2>&1 &
    echo $! > "$LOG_DIR/celery.pid"
    sleep 2
    # Verify it actually started (not immediately died)
    if kill -0 "$(cat "$LOG_DIR/celery.pid")" 2>/dev/null; then
        info "Celery worker started (PID $(cat "$LOG_DIR/celery.pid"))"
    else
        warn "Celery failed to start — check $LOG_DIR/celery.log"
        tail -5 "$LOG_DIR/celery.log"
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────
stop_old
start_postgres
start_redis
setup_cuda
start_uvicorn
start_celery

echo ""
echo -e "${GREEN}✓ 所有服务已启动${NC}"
echo -e "  API:     http://localhost:8000"
echo -e "  日志:    $LOG_DIR/"
echo -e "  停止:    bash stop-backend.sh"
