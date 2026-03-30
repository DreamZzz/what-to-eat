#!/bin/bash
# 完整前端开发环境启动脚本
# 类似于后端start.sh的设计理念
# 启动Metro开发服务器 + iOS应用
# 用法: ./dev.sh

set -e

cd "$(dirname "$0")"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 获取合适的bundle命令
get_bundle_cmd() {
    # 检查Homebrew Ruby的bundle
    if [ -f "/opt/homebrew/Cellar/ruby/4.0.1/bin/bundle" ]; then
        echo "/opt/homebrew/Cellar/ruby/4.0.1/bin/bundle"
    elif [ -f "/opt/homebrew/bin/bundle" ]; then
        echo "/opt/homebrew/bin/bundle"
    else
        # 使用系统bundle
        echo "bundle"
    fi
}

# 清理函数
cleanup() {
    print_info "正在清理..."
    
    # 停止Metro进程
    if [ -n "$METRO_PID" ]; then
        print_info "停止Metro进程 (PID: $METRO_PID)"
        kill $METRO_PID 2>/dev/null || true
    fi
    
    # 停止iOS模拟器
    if [ "$(uname)" = "Darwin" ]; then
        print_info "停止iOS模拟器"
        killall "Simulator" 2>/dev/null || true
    fi
    
    print_success "清理完成"
    exit 0
}

# 注册清理函数
trap cleanup SIGINT SIGTERM EXIT

# 环境检查
check_environment() {
    print_info "检查开发环境..."
    
    # 检查Node.js
    if ! command -v node >/dev/null 2>&1; then
        print_error "Node.js未安装，请先安装Node.js"
        exit 1
    fi
    
    # 检查npm
    if ! command -v npm >/dev/null 2>&1; then
        print_error "npm未安装，请先安装npm"
        exit 1
    fi
    
    # 检查React Native CLI
    if ! npx react-native --version >/dev/null 2>&1; then
        print_warning "React Native CLI未全局安装，将使用npx"
    fi
    
    print_success "环境检查通过"
    print_info "Node版本: $(node --version)"
    print_info "npm版本: $(npm --version)"
}

# 安装依赖
install_dependencies() {
    print_info "检查依赖..."
    
    if [ ! -d "node_modules" ]; then
        print_info "安装项目依赖..."
        npm install
        print_success "项目依赖安装完成"
    else
        print_info "项目依赖已安装"
    fi
    
    # macOS环境下安装iOS依赖
    if [ "$(uname)" = "Darwin" ]; then
        if [ ! -d "ios/Pods" ]; then
            print_info "安装iOS CocoaPods依赖..."
            cd ios
            # 获取合适的bundle命令
            BUNDLE_CMD=$(get_bundle_cmd)
            print_info "使用bundle命令: $BUNDLE_CMD"
            
            if ! $BUNDLE_CMD exec pod install --no-repo-update; then
                print_error "CocoaPods安装失败，请检查网络和RubyGems源配置"
                print_info "尝试使用阿里云镜像：sed -i '' 's|^source .*|source \"https://mirrors.aliyun.com/rubygems/\"|' ../Gemfile"
                exit 1
            fi
            cd ..
            print_success "iOS依赖安装完成"
        else
            print_info "iOS依赖已安装"
        fi
    fi
}

# 启动Metro开发服务器（后台）
start_metro_background() {
    print_info "启动Metro开发服务器（后台）..."
    
    # 清理旧的Metro日志
    rm -f /tmp/metro.log 2>/dev/null || true
    
    # 启动Metro服务器
    npx react-native start --reset-cache > /tmp/metro.log 2>&1 &
    METRO_PID=$!
    
    # 等待Metro启动
    print_info "等待Metro服务器启动..."
    sleep 8
    
    # 检查Metro是否运行
    if kill -0 $METRO_PID 2>/dev/null; then
        print_success "Metro服务器已启动 (PID: $METRO_PID)"
        print_info "Metro日志: /tmp/metro.log"
    else
        print_error "Metro服务器启动失败"
        print_info "查看日志: /tmp/metro.log"
        cat /tmp/metro.log | tail -20
        exit 1
    fi
}

# 启动iOS应用
start_ios_app() {
    if [ "$(uname)" != "Darwin" ]; then
        print_warning "非macOS系统，跳过iOS启动"
        return 0
    fi
    
    print_info "启动iOS应用..."
    
    # 检查Xcode
    if ! xcodebuild -version >/dev/null 2>&1; then
        print_error "Xcode未安装或未配置"
        exit 1
    fi
    
    # 启动iOS应用
    npx react-native run-ios &
    IOS_PID=$!
    
    print_success "iOS应用已启动 (PID: $IOS_PID)"
}

# 健康检查
health_check() {
    print_info "执行健康检查..."
    
    # 检查Metro服务器
    if ! kill -0 $METRO_PID 2>/dev/null; then
        print_error "Metro服务器已停止"
        return 1
    fi
    
    print_success "健康检查通过"
    return 0
}

# 显示状态信息
show_status() {
    echo ""
    print_success "=== 前端开发环境已就绪 ==="
    echo ""
    print_info "📱 应用状态:"
    print_info "  - Metro服务器: 运行中 (PID: $METRO_PID)"
    
    if [ -n "$IOS_PID" ] && kill -0 $IOS_PID 2>/dev/null; then
        print_info "  - iOS应用: 运行中 (PID: $IOS_PID)"
    else
        print_info "  - iOS应用: 未运行"
    fi
    
    echo ""
    print_info "🔧 开发工具:"
    print_info "  - 重新加载应用: Cmd+R (iOS模拟器)"
    print_info "  - 打开开发者菜单: Cmd+D (iOS模拟器)"
    print_info "  - 清除缓存并重新加载: Cmd+Shift+R"
    
    echo ""
    print_info "📊 日志信息:"
    print_info "  - Metro日志: /tmp/metro.log"
    print_info "  - 查看Metro日志: tail -f /tmp/metro.log"
    
    echo ""
    print_info "🚦 停止开发环境: 按 Ctrl+C"
    echo ""
}

# 主函数
main() {
    print_info "=== 完整前端开发环境启动脚本 ==="
    print_info "项目目录: $(pwd)"
    print_info "启动时间: $(date)"
    
    # 环境检查
    check_environment
    
    # 安装依赖
    install_dependencies
    
    # 启动Metro服务器
    start_metro_background
    
    # 启动iOS应用
    start_ios_app
    
    # 健康检查
    health_check
    
    # 显示状态信息
    show_status
    
    # 等待用户中断
    print_info "等待用户中断 (Ctrl+C)..."
    wait
}

# 执行主函数
main "$@"