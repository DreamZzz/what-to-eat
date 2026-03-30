#!/bin/bash

# restart-app.sh
# 重启小红书社交应用（前端React Native + 后端Spring Boot）
# 用法: ./restart-app.sh [--restart-backend]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

# 模拟器设置
SIMULATOR_NAME="iPhone 17"
SIMULATOR_UDID="6C143B2E-EABF-44D4-8462-3CB7FCC2FF62"
APP_BUNDLE_ID="org.reactjs.native.example.frontend"

# 日志文件
BACKEND_LOG="$BACKEND_DIR/backend.log"
METRO_LOG="/tmp/metro.log"

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v "$1" &> /dev/null; then
        print_error "命令 '$1' 未找到，请先安装"
        exit 1
    fi
}

# 停止进程
stop_process() {
    local process_name="$1"
    local pids=$(pgrep -f "$process_name" || true)
    
    if [ -n "$pids" ]; then
        print_info "停止 $process_name 进程 (PID: $pids)"
        kill -9 $pids 2>/dev/null || true
        sleep 1
    else
        print_info "$process_name 进程未在运行"
    fi
}

# 停止iOS应用
stop_ios_app() {
    print_info "停止iOS应用 ($APP_BUNDLE_ID)"
    xcrun simctl terminate "$SIMULATOR_UDID" "$APP_BUNDLE_ID" 2>/dev/null || true
}

# 检查后端是否在运行
check_backend_running() {
    if ps aux | grep -v grep | grep -q "java.*spring-boot:run"; then
        return 0
    else
        return 1
    fi
}

# 启动后端Spring Boot服务
start_backend() {
    print_info "启动后端Spring Boot服务..."
    
    if check_backend_running; then
        print_warning "后端服务已在运行，跳过启动"
        return 0
    fi
    
    cd "$BACKEND_DIR"
    
    print_info "清理并编译项目..."
    mvn clean compile -q
    
    print_info "启动Spring Boot应用 (日志: $BACKEND_LOG)"
    nohup mvn spring-boot:run > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    
    sleep 3
    
    if ps -p $BACKEND_PID > /dev/null; then
        print_success "后端服务启动成功 (PID: $BACKEND_PID)"
        print_info "日志文件: $BACKEND_LOG"
        
        # 等待服务完全启动
        print_info "等待后端服务就绪..."
        for i in {1..30}; do
            if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health > /dev/null 2>&1; then
                print_success "后端服务已就绪 (http://localhost:8080)"
                return 0
            fi
            sleep 1
        done
        print_warning "后端服务启动较慢，请稍后手动检查"
    else
        print_error "后端服务启动失败，请检查日志: $BACKEND_LOG"
        return 1
    fi
}

# 启动Metro打包器
start_metro() {
    print_info "启动Metro打包器..."
    
    stop_process "react-native start"
    stop_process "node.*metro"
    
    cd "$FRONTEND_DIR"
    
    print_info "安装依赖 (如果缺失)..."
    if [ ! -d "node_modules" ]; then
        npm ci --silent
    fi
    
    print_info "启动Metro开发服务器 (日志: $METRO_LOG)"
    nohup npx react-native start --reset-cache > "$METRO_LOG" 2>&1 &
    METRO_PID=$!
    
    sleep 2
    
    if ps -p $METRO_PID > /dev/null; then
        print_success "Metro打包器启动成功 (PID: $METRO_PID)"
        print_info "日志文件: $METRO_LOG"
    else
        print_error "Metro打包器启动失败，请检查日志: $METRO_LOG"
        return 1
    fi
}

# 启动iOS应用
start_ios_app() {
    print_info "启动iOS应用..."
    
    # 确保模拟器正在运行
    if ! xcrun simctl list | grep -q "$SIMULATOR_UDID.*Booted"; then
        print_info "启动模拟器: $SIMULATOR_NAME"
        xcrun simctl boot "$SIMULATOR_UDID"
        sleep 5
    fi
    
    # 停止现有应用
    stop_ios_app
    
    cd "$FRONTEND_DIR"
    
    print_info "构建并启动iOS应用..."
    # 在后台启动应用
    npx react-native run-ios --simulator="$SIMULATOR_NAME" --no-packager &
    IOS_BUILD_PID=$!
    
    # 等待构建完成
    sleep 10
    
    # 检查应用是否在运行
    if xcrun simctl launch "$SIMULATOR_UDID" "$APP_BUNDLE_ID" > /dev/null 2>&1; then
        print_success "iOS应用启动成功"
        print_info "模拟器: $SIMULATOR_NAME (UDID: $SIMULATOR_UDID)"
        print_info "应用: $APP_BUNDLE_ID"
    else
        print_warning "iOS应用启动可能有问题，请检查模拟器"
    fi
}

# 检查依赖
check_dependencies() {
    print_info "检查必要依赖..."
    
    check_command "node"
    check_command "npm"
    check_command "mvn"
    check_command "xcrun"
    check_command "curl"
    
    print_success "所有依赖已就绪"
}

# 显示状态
show_status() {
    echo ""
    print_info "=== 应用状态 ==="
    
    if check_backend_running; then
        echo -e "后端服务: ${GREEN}运行中${NC}"
    else
        echo -e "后端服务: ${RED}未运行${NC}"
    fi
    
    if pgrep -f "react-native start" > /dev/null; then
        echo -e "Metro打包器: ${GREEN}运行中${NC}"
    else
        echo -e "Metro打包器: ${RED}未运行${NC}"
    fi
    
    if xcrun simctl list | grep -q "$SIMULATOR_UDID.*Booted"; then
        echo -e "iOS模拟器: ${GREEN}已启动${NC}"
    else
        echo -e "iOS模拟器: ${RED}未启动${NC}"
    fi
    
    echo ""
    print_info "访问地址:"
    echo "  - 后端API: http://localhost:8080"
    echo "  - 后端日志: $BACKEND_LOG"
    echo "  - Metro日志: $METRO_LOG"
    echo ""
    print_info "测试账号:"
    echo "  - 用户名: testuser"
    echo "  - 密码: password123"
}

# 主函数
main() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}      小红书社交应用重启脚本${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    
    # 解析参数
    RESTART_BACKEND=false
    for arg in "$@"; do
        case $arg in
            --restart-backend)
                RESTART_BACKEND=true
                shift
                ;;
            -h|--help)
                echo "用法: $0 [选项]"
                echo "选项:"
                echo "  --restart-backend    重启后端服务（默认不重启）"
                echo "  -h, --help          显示此帮助信息"
                exit 0
                ;;
        esac
    done
    
    # 检查依赖
    check_dependencies
    
    # 停止现有进程
    print_info "停止现有进程..."
    stop_ios_app
    stop_process "react-native start"
    stop_process "node.*metro"
    
    # 后端服务
    if [ "$RESTART_BACKEND" = true ]; then
        print_info "重启后端服务..."
        stop_process "java.*spring-boot:run"
        start_backend
    else
        if check_backend_running; then
            print_info "后端服务已在运行，跳过重启"
        else
            print_warning "后端服务未运行，正在启动..."
            start_backend
        fi
    fi
    
    # 前端服务
    print_info "启动前端服务..."
    start_metro
    start_ios_app
    
    # 显示状态
    show_status
    
    echo ""
    print_success "应用重启完成！"
    print_info "请在模拟器中测试应用功能"
    echo -e "${BLUE}========================================${NC}"
}

# 运行主函数
main "$@"