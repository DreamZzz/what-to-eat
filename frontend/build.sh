#!/bin/bash
# 前端生产打包脚本
# 用法: ./build.sh [ios|android]
# 示例:
#   ./build.sh ios      # 构建iOS生产包
#   ./build.sh android  # 构建Android生产包
#   ./build.sh          # 默认构建iOS

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

# 环境检查
check_environment() {
    print_info "检查构建环境..."
    
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
    
    print_success "环境检查通过"
    print_info "Node版本: $(node --version)"
    print_info "npm版本: $(npm --version)"
}

# 清理构建缓存
clean_build() {
    print_info "清理构建缓存..."
    
    # 清理React Native缓存
    npx react-native-clean-project --remove-iOS-build --remove-iOS-pods --keep-node-modules
    
    # 清理npm缓存
    npm cache clean --force
    
    print_success "构建缓存清理完成"
}

# 构建iOS生产包
build_ios() {
    if [ "$(uname)" != "Darwin" ]; then
        print_error "iOS构建需要macOS系统"
        exit 1
    fi
    
    print_info "开始构建iOS生产包..."
    
    # 检查Xcode
    if ! xcodebuild -version >/dev/null 2>&1; then
        print_error "Xcode未安装或未配置"
        exit 1
    fi
    
    # 安装依赖
    if [ ! -d "node_modules" ]; then
        print_info "安装项目依赖..."
        npm install
    fi
    
    if [ ! -d "ios/Pods" ]; then
        print_info "安装CocoaPods依赖..."
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
    fi
    
    # 切换到iOS目录
    cd ios
    
    # 清理Xcode构建
    print_info "清理Xcode构建..."
    xcodebuild clean -workspace frontend.xcworkspace -scheme frontend -configuration Release
    
    # 构建iOS应用
    print_info "构建iOS应用..."
    xcodebuild archive \
        -workspace frontend.xcworkspace \
        -scheme frontend \
        -configuration Release \
        -archivePath ../build/frontend.xcarchive \
        CODE_SIGNING_ALLOWED=NO \
        CODE_SIGNING_REQUIRED=NO \
        CODE_SIGN_IDENTITY="" \
        | xcpretty
    
    if [ $? -ne 0 ]; then
        print_error "iOS构建失败"
        exit 1
    fi
    
    cd ..
    
    print_success "iOS构建完成!"
    print_info "输出位置: $(pwd)/build/frontend.xcarchive"
}

# 构建Android生产包
build_android() {
    print_info "开始构建Android生产包..."
    
    # 检查Java
    if ! command -v java >/dev/null 2>&1; then
        print_error "Java未安装，请先安装Java JDK"
        exit 1
    fi
    
    # 检查Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        print_warning "ANDROID_HOME环境变量未设置"
        if [ -d "$HOME/Library/Android/sdk" ]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
            print_info "使用默认Android SDK路径: $ANDROID_HOME"
        else
            print_error "未找到Android SDK，请设置ANDROID_HOME环境变量"
            exit 1
        fi
    fi
    
    # 检查Gradle
    if [ ! -f "android/gradlew" ]; then
        print_error "未找到Gradle wrapper"
        exit 1
    fi
    
    # 安装依赖
    if [ ! -d "node_modules" ]; then
        print_info "安装项目依赖..."
        npm install
    fi
    
    # 切换到Android目录
    cd android
    
    # 清理Gradle构建
    print_info "清理Gradle构建..."
    ./gradlew clean
    
    # 构建Android应用
    print_info "构建Android应用..."
    ./gradlew assembleRelease \
        --no-daemon \
        --max-workers=4
    
    if [ $? -ne 0 ]; then
        print_error "Android构建失败"
        exit 1
    fi
    
    cd ..
    
    print_success "Android构建完成!"
    print_info "输出位置: $(pwd)/android/app/build/outputs/apk/release/"
    print_info "APK文件: $(pwd)/android/app/build/outputs/apk/release/app-release.apk"
}

# 主函数
main() {
    print_info "=== 前端生产打包脚本 ==="
    print_info "项目目录: $(pwd)"
    print_info "构建时间: $(date)"
    
    # 环境检查
    check_environment
    
    # 清理构建缓存（可选）
    if [ "${CLEAN_BUILD:-false}" = "true" ]; then
        clean_build
    fi
    
    # 解析参数
    PLATFORM="${1:-ios}"
    
    case "$PLATFORM" in
        ios)
            build_ios
            ;;
        android)
            build_android
            ;;
        *)
            print_error "无效的平台: $PLATFORM"
            print_info "用法: $0 [ios|android]"
            print_info "环境变量:"
            print_info "  CLEAN_BUILD=true  # 清理构建缓存"
            exit 1
            ;;
    esac
    
    print_success "打包完成!"
    print_info "总耗时: $(($SECONDS / 60))分钟$(($SECONDS % 60))秒"
}

# 记录开始时间
SECONDS=0

# 执行主函数
main "$@"