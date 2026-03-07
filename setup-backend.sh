#!/usr/bin/env bash
# setup-backend.sh — 一键初始化后端环境（首次部署时运行）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$SCRIPT_DIR/backend"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[setup]${NC} $*"; }
warn()  { echo -e "${YELLOW}[warn]${NC}  $*"; }
error() { echo -e "${RED}[error]${NC} $*"; exit 1; }
step()  { echo -e "\n${GREEN}══ $* ══${NC}"; }

# ── 1. Python 版本检查 ────────────────────────────────────────────────────────
step "检查 Python"
PYTHON=$(command -v python3.12 || command -v python3 || error "未找到 python3")
PY_VER=$($PYTHON -c "import sys; print('%d.%d' % sys.version_info[:2])")
info "使用 $PYTHON ($PY_VER)"
[[ "$PY_VER" < "3.10" ]] && error "需要 Python 3.10+，当前 $PY_VER"

# ── 2. 系统依赖检查 ───────────────────────────────────────────────────────────
step "检查系统依赖"
for cmd in redis-cli pg_isready ffmpeg; do
    if command -v "$cmd" &>/dev/null; then
        info "$cmd: OK"
    else
        warn "$cmd 未找到 — 请手动安装"
    fi
done

# ── 3. 创建虚拟环境 ───────────────────────────────────────────────────────────
step "创建 Python 虚拟环境"
if [[ -d "$BACKEND/.venv" ]]; then
    info ".venv 已存在，跳过创建"
else
    $PYTHON -m venv "$BACKEND/.venv"
    info "虚拟环境创建完成: $BACKEND/.venv"
fi
source "$BACKEND/.venv/bin/activate"

# ── 4. 安装依赖 ───────────────────────────────────────────────────────────────
step "安装 Python 依赖"
pip install --upgrade pip -q
pip install -r "$BACKEND/requirements.txt"
info "依赖安装完成"

# ── 5. 配置 .env ──────────────────────────────────────────────────────────────
step "配置环境变量"
if [[ -f "$BACKEND/.env" ]]; then
    info ".env 已存在，跳过复制"
else
    cp "$BACKEND/.env.example" "$BACKEND/.env"
    warn ".env 已从 .env.example 复制，请编辑填入真实配置："
    warn "  $BACKEND/.env"
fi

# ── 6. 创建数据库 ─────────────────────────────────────────────────────────────
step "初始化 PostgreSQL 数据库"
DB_NAME="digestit"
DB_USER="digest"
DB_PASS="digest"

if pg_isready -q 2>/dev/null; then
    # 创建用户（若不存在）
    sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" \
        | grep -q 1 && info "数据库用户 $DB_USER 已存在" || \
        sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';" \
        && info "创建用户 $DB_USER"

    # 创建数据库（若不存在）
    sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" \
        | grep -q 1 && info "数据库 $DB_NAME 已存在" || \
        sudo -u postgres psql -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" \
        && info "创建数据库 $DB_NAME"

    # 初始化表结构
    cd "$BACKEND"
    python -c "from app.core.database import init_db; init_db()"
    info "数据库表结构初始化完成"
else
    warn "PostgreSQL 未运行，跳过数据库初始化（启动后请手动运行）："
    warn "  source backend/.venv/bin/activate"
    warn "  cd backend && python -c 'from app.core.database import init_db; init_db()'"
fi

# ── 7. 创建音频临时目录 ───────────────────────────────────────────────────────
step "创建临时目录"
AUDIO_DIR=$(grep AUDIO_TMP_DIR "$BACKEND/.env" | cut -d= -f2 || echo "/tmp/digestit_audio")
mkdir -p "$AUDIO_DIR"
info "音频临时目录: $AUDIO_DIR"

# ── 完成 ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}✓ 初始化完成！${NC}"
echo ""
echo -e "  下一步："
echo -e "  1. 编辑 ${YELLOW}backend/.env${NC} 填入 API Key 和数据库密码"
echo -e "  2. 运行 ${GREEN}bash start-backend.sh${NC} 启动服务"
