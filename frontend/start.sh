#!/bin/bash
# 前端开发启动脚本
# 用法: ./start.sh [ios|android|device|metro] [local|remote] [ios|android|device] [设备名]
# 也支持简写:
#   ./start.sh local                    # 等价于 ./start.sh ios local
#   ./start.sh remote                   # 等价于 ./start.sh ios remote
#   ./start.sh local "你的iPhone名称"     # 等价于 ./start.sh device local "你的iPhone名称"
#   ./start.sh remote "你的iPhone名称"    # 等价于 ./start.sh device remote "你的iPhone名称"
# 示例: 
#   ./start.sh ios local     # 启动iOS本地联调环境
#   ./start.sh ios remote    # 启动iOS线上联调环境
#   ./start.sh device remote # 启动iPhone真机联调（连线上ECS）
#   ./start.sh android local # 启动Android本地联调环境
#   ./start.sh metro remote  # 只启动Metro并写入远端配置
#   ./start.sh               # 默认启动iOS本地联调环境

set -euo pipefail

cd "$(dirname "$0")"

sync_contract_models() {
    print_info "同步后端 DTO -> 前端 Model / Swift Codable ..."
    node ../scripts/generate-contract-models.mjs
}

load_local_env() {
    if [ -f ".env.local" ]; then
        print_info "检测到 .env.local 文件，加载前端运行时环境变量..."
        set -a
        # shellcheck disable=SC1091
        . ./.env.local
        set +a
    fi
}

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

sync_contract_models
load_local_env

APP_ENV="local"
API_BASE_URL=""
PROXY_TARGET=""
PROXY_BIND_HOST="127.0.0.1"
CONFIG_PLATFORM="ios"
TARGET_DEVICE_NAME=""
IOS_APP_BUNDLE_ID="${IOS_APP_BUNDLE_ID:-com.quickstart.template.frontend}"
DEFAULT_REMOTE_API_BASE_URL="${APP_REMOTE_API_BASE_URL:-https://api.example.com}"
DEFAULT_REMOTE_API_BASE_URL="${DEFAULT_REMOTE_API_BASE_URL%/}"
DEFAULT_REMOTE_PROXY_TARGET="${APP_REMOTE_PROXY_TARGET:-$DEFAULT_REMOTE_API_BASE_URL}"
DEFAULT_REMOTE_PROXY_TARGET="${DEFAULT_REMOTE_PROXY_TARGET%/}"

list_connected_ios_devices() {
    xcrun xcdevice list 2>/dev/null | node -e '
const fs = require("fs");
let raw = "";
process.stdin.on("data", chunk => raw += chunk);
process.stdin.on("end", () => {
  try {
    const data = JSON.parse(raw);
    const devices = data.filter(device =>
      device &&
      device.available === true &&
      device.simulator === false &&
      device.platform !== "com.apple.platform.macosx"
    );
    for (const device of devices) {
      const name = (device.name || "").trim();
      const id = (device.identifier || "").trim();
      if (name && id) {
        console.log(`${name}\t${id}`);
      }
    }
  } catch (error) {
    process.exit(0);
  }
});
'
}

list_available_ios_simulators() {
    xcrun simctl list devices available 2>/dev/null | node -e '
const fs = require("fs");
const lines = fs.readFileSync(0, "utf8").split(/\r?\n/);
let runtime = "";

for (const line of lines) {
  const runtimeMatch = line.match(/^-- (iOS [^-]+) --$/);
  if (runtimeMatch) {
    runtime = runtimeMatch[1].trim();
    continue;
  }

  const deviceMatch = line.match(/^\s+(.+?) \(([0-9A-F-]+)\) \((Booted|Shutdown)\)\s*$/);
  if (!deviceMatch || !runtime.startsWith("iOS ")) {
    continue;
  }

  const name = deviceMatch[1].trim();
  const udid = deviceMatch[2].trim();
  const state = deviceMatch[3].trim();

  console.log(`${name}\t${udid}\t${runtime}\t${state}`);
}
'
}

get_booted_ios_simulator_udid() {
    list_available_ios_simulators | awk -F '\t' '$4 == "Booted" { print $2; exit }'
}

get_ios_simulator_name_by_udid() {
    local requested_udid="$1"

    if [ -z "$requested_udid" ]; then
        return 0
    fi

    list_available_ios_simulators | awk -F '\t' -v requested="$requested_udid" '$2 == requested { print $1; exit }'
}

get_first_available_ios_simulator_udid() {
    list_available_ios_simulators | awk -F '\t' '$1 ~ /^iPhone / { print $2; exit }'
}

ensure_ios_simulator_booted() {
    local simulator_udid="$1"

    if [ -z "$simulator_udid" ]; then
        return 1
    fi

    xcrun simctl boot "$simulator_udid" >/dev/null 2>&1 || true
    xcrun simctl bootstatus "$simulator_udid" -b >/dev/null 2>&1 || true

    if command -v open >/dev/null 2>&1; then
        open -a Simulator --args -CurrentDeviceUDID "$simulator_udid" >/dev/null 2>&1 || true
    fi

    return 0
}

get_connected_ios_device_name() {
    list_connected_ios_devices | head -n 1 | cut -f 1
}

resolve_target_ios_device_name() {
    local requested_name="$1"

    if [ -z "$requested_name" ]; then
        get_connected_ios_device_name
        return 0
    fi

    list_connected_ios_devices | node -e '
const fs = require("fs");
const requested = (process.argv[1] || "").trim().toLowerCase();
const lines = fs.readFileSync(0, "utf8").split(/\r?\n/).filter(Boolean);
const devices = lines.map(line => {
  const [name, id] = line.split("\t");
  return { name, id };
});

const exact = devices.find(device => device.name.toLowerCase() === requested || device.id.toLowerCase() === requested);
if (exact) {
  process.stdout.write(exact.name);
  process.exit(0);
}

const partial = devices.find(device => device.name.toLowerCase().includes(requested) || device.id.toLowerCase().includes(requested));
if (partial) {
  process.stdout.write(partial.name);
}
' "$requested_name"
}

print_available_ios_devices() {
    local devices
    devices="$(list_connected_ios_devices || true)"

    if [ -z "$devices" ]; then
        echo "- 未检测到已连接的 iPhone/iPad"
        return 0
    fi

    while IFS=$'\t' read -r name id; do
        if [ -n "$name" ] && [ -n "$id" ]; then
            echo "- $name ($id)"
        fi
    done <<< "$devices"
}

launch_ios_app_on_device() {
    local device_name="$1"

    if [ -z "$device_name" ]; then
        return 1
    fi

    if ! command -v xcrun >/dev/null 2>&1; then
        return 1
    fi

    xcrun devicectl device process launch \
        --device "$device_name" \
        --terminate-existing \
        "$IOS_APP_BUNDLE_ID" >/tmp/quickstart-template-devicectl-launch.log 2>&1
}

detect_local_lan_ip() {
    local preferred_interface
    local ip

    preferred_interface=$(route -n get default 2>/dev/null | awk '/interface: / {print $2; exit}' || true)

    if [ -n "$preferred_interface" ]; then
        ip=$(ifconfig "$preferred_interface" 2>/dev/null | awk '/inet / {print $2; exit}' || true)
        if [ -n "$ip" ] && [[ ! "$ip" =~ ^169\.254\. ]]; then
            echo "$ip"
            return 0
        fi
    fi

    ip=$(ifconfig | awk '
        /^[a-z0-9]+: / {
            iface=$1
            sub(":", "", iface)
            active=0
            next
        }
        /status: active/ {
            active=1
            next
        }
        active && /inet / {
            candidate=$2
            if (candidate != "127.0.0.1" && candidate !~ /^169\.254\./) {
                print candidate
                exit
            }
        }
    ' || true)

    echo "$ip"
}

resolve_runtime_config() {
    PROXY_BIND_HOST="127.0.0.1"

    case "$APP_ENV" in
        local)
            if [ "$CONFIG_PLATFORM" = "ios" ]; then
                API_BASE_URL="http://127.0.0.1:18080"
                PROXY_TARGET="http://127.0.0.1:8080"
            elif [ "$CONFIG_PLATFORM" = "device" ]; then
                local_lan_ip="${LOCAL_LAN_IP:-$(detect_local_lan_ip)}"
                if [ -z "$local_lan_ip" ]; then
                    print_error "无法自动识别本机局域网IP。真机本地联调请先设置 LOCAL_LAN_IP"
                    print_info "示例: LOCAL_LAN_IP=192.168.1.23 ./start.sh device local"
                    exit 1
                fi
                # Physical devices cannot reach the Mac-only loopback proxy, so local
                # mode exposes the proxy on the LAN and keeps the backend itself bound
                # to localhost:8080 on the host.
                API_BASE_URL="http://${local_lan_ip}:18080"
                PROXY_TARGET="http://127.0.0.1:8080"
                PROXY_BIND_HOST="0.0.0.0"
            else
                API_BASE_URL="http://10.0.2.2:8080"
                PROXY_TARGET="http://127.0.0.1:8080"
            fi
            ;;
        remote)
            if [ "$CONFIG_PLATFORM" = "ios" ]; then
                API_BASE_URL="http://127.0.0.1:18080"
                PROXY_TARGET="$DEFAULT_REMOTE_PROXY_TARGET"
            elif [ "$CONFIG_PLATFORM" = "device" ]; then
                API_BASE_URL="$DEFAULT_REMOTE_API_BASE_URL"
                PROXY_TARGET=""
            else
                API_BASE_URL="$DEFAULT_REMOTE_API_BASE_URL"
                PROXY_TARGET="$DEFAULT_REMOTE_PROXY_TARGET"
            fi
            ;;
        *)
            print_error "无效的环境: $APP_ENV"
            print_info "可用环境: local | remote"
            exit 1
            ;;
    esac
}

write_runtime_config() {
    print_info "写入运行时配置..."
    node scripts/write-runtime-config.js "$APP_ENV" "$API_BASE_URL" "$PROXY_TARGET"
    print_info "当前环境: $APP_ENV"
    print_info "API地址: $API_BASE_URL"
    if [ "$APP_ENV" = "remote" ]; then
        if [ -n "$PROXY_TARGET" ]; then
            print_info "远端上游: $PROXY_TARGET"
        else
            print_info "远端上游: $API_BASE_URL"
        fi
    fi
}

set_or_add_plist_string() {
    local plist_path="$1"
    local key="$2"
    local value="$3"
    local plistbuddy="/usr/libexec/PlistBuddy"

    if $plistbuddy -c "Set :$key $value" "$plist_path" >/dev/null 2>&1; then
        return 0
    fi

    $plistbuddy -c "Add :$key string $value" "$plist_path"
}

sync_ios_wechat_config() {
    if [ "$(uname)" != "Darwin" ]; then
        return 0
    fi

    local plist_path="ios/frontend/Info.plist"
    local entitlements_path="ios/frontend/frontend.entitlements"
    local plistbuddy="/usr/libexec/PlistBuddy"
    local wechat_app_id="${APP_SHARE_WECHAT_APP_ID:-}"
    local wechat_universal_link="${APP_SHARE_WECHAT_UNIVERSAL_LINK:-}"
    local universal_link_host=""

    if [ ! -x "$plistbuddy" ] || [ ! -f "$plist_path" ]; then
        return 0
    fi

    set_or_add_plist_string "$plist_path" "WechatAppID" "$wechat_app_id"
    set_or_add_plist_string "$plist_path" "WechatUniversalLink" "$wechat_universal_link"

    $plistbuddy -c "Delete :CFBundleURLTypes" "$plist_path" >/dev/null 2>&1 || true

    if [ -n "$wechat_app_id" ]; then
        $plistbuddy -c "Add :CFBundleURLTypes array" "$plist_path"
        $plistbuddy -c "Add :CFBundleURLTypes:0 dict" "$plist_path"
        $plistbuddy -c "Add :CFBundleURLTypes:0:CFBundleTypeRole string Editor" "$plist_path"
        $plistbuddy -c "Add :CFBundleURLTypes:0:CFBundleURLName string wechat" "$plist_path"
        $plistbuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$plist_path"
        $plistbuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string $wechat_app_id" "$plist_path"
    else
        print_warning "未配置 APP_SHARE_WECHAT_APP_ID，微信好友/朋友圈分享将保持不可用，系统分享不受影响"
    fi

    if [ -n "$wechat_universal_link" ]; then
        universal_link_host=$(printf '%s' "$wechat_universal_link" | sed -nE 's#^https?://([^/]+)/?.*$#\1#p')
    fi

    if [ -f "$entitlements_path" ]; then
        $plistbuddy -c "Delete :com.apple.developer.associated-domains" "$entitlements_path" >/dev/null 2>&1 || true

        if [ -n "$universal_link_host" ]; then
            $plistbuddy -c "Add :com.apple.developer.associated-domains array" "$entitlements_path"
            $plistbuddy -c "Add :com.apple.developer.associated-domains:0 string applinks:$universal_link_host" "$entitlements_path"
        fi
    fi

    if [ -z "$wechat_universal_link" ]; then
        print_warning "未配置 APP_SHARE_WECHAT_UNIVERSAL_LINK，iOS 微信直分享将保持不可用，系统分享不受影响"
    elif [ -z "$universal_link_host" ]; then
        print_warning "APP_SHARE_WECHAT_UNIVERSAL_LINK 不是合法的 https URL，无法写入 Associated Domains"
    fi
}

stop_existing_proxy() {
    local proxy_pids
    proxy_pids=$(lsof -ti tcp:18080 2>/dev/null || true)

    if [ -n "$proxy_pids" ]; then
        print_warning "检测到已有本地API代理占用18080，正在停止..."
        echo "$proxy_pids" | xargs kill 2>/dev/null || true
        sleep 1
    fi
}

start_api_proxy_background() {
    print_info "启动本地API代理（${PROXY_BIND_HOST}:18080 -> ${PROXY_TARGET}）..."
    stop_existing_proxy

    rm -f /tmp/social-app-api-proxy.log 2>/dev/null || true
    DEV_PROXY_TARGET_HOST=$(echo "$PROXY_TARGET" | sed -E 's#^http://([^:/]+).*$#\1#')
    DEV_PROXY_TARGET_PORT=$(echo "$PROXY_TARGET" | sed -nE 's#^http://[^:/]+:([0-9]+).*$#\1#p')
    if [ -z "$DEV_PROXY_TARGET_PORT" ]; then
        DEV_PROXY_TARGET_PORT=80
    fi
    DEV_PROXY_BIND_HOST="$PROXY_BIND_HOST" DEV_PROXY_TARGET_HOST="$DEV_PROXY_TARGET_HOST" DEV_PROXY_TARGET_PORT="$DEV_PROXY_TARGET_PORT" \
        nohup node scripts/dev-proxy.js > /tmp/social-app-api-proxy.log 2>&1 &
    PROXY_PID=$!

    sleep 2

    if kill -0 "$PROXY_PID" 2>/dev/null; then
        print_success "本地API代理已启动 (PID: $PROXY_PID)"
        print_info "代理日志: /tmp/social-app-api-proxy.log"
    else
        print_error "本地API代理启动失败"
        tail -20 /tmp/social-app-api-proxy.log 2>/dev/null || true
        exit 1
    fi
}

stop_existing_metro() {
    local metro_pids
    metro_pids=$(lsof -ti tcp:8081 2>/dev/null || true)

    if [ -n "$metro_pids" ]; then
        print_warning "检测到已有Metro进程占用8081，正在停止..."
        echo "$metro_pids" | xargs kill 2>/dev/null || true
        sleep 2
    fi
}

start_metro_background() {
    print_info "启动Metro开发服务器（后台）..."
    stop_existing_metro

    rm -f /tmp/social-app-metro.log 2>/dev/null || true
    nohup npx react-native start --reset-cache > /tmp/social-app-metro.log 2>&1 &
    METRO_PID=$!

    print_info "等待Metro服务器启动..."
    sleep 8

    if kill -0 "$METRO_PID" 2>/dev/null; then
        print_success "Metro服务器已启动 (PID: $METRO_PID)"
        print_info "Metro日志: /tmp/social-app-metro.log"
    else
        print_error "Metro服务器启动失败"
        print_info "查看日志: /tmp/social-app-metro.log"
        tail -20 /tmp/social-app-metro.log 2>/dev/null || true
        exit 1
    fi
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
    if [ ! -d "node_modules" ]; then
        print_info "安装依赖..."
        npm install
        print_success "依赖安装完成"
    else
        print_info "依赖已安装"
    fi
}

# 安装iOS依赖
install_ios_deps() {
    if [ "$(uname)" = "Darwin" ]; then
        print_info "检查iOS依赖..."
        
        # 检查Ruby版本，建议使用Ruby 2.7+或Homebrew Ruby
        RUBY_VERSION=$(ruby --version | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | head -1)
        if [[ "$(printf '%s\n' "2.7.0" "$RUBY_VERSION" | sort -V | head -n1)" != "2.7.0" ]]; then
            print_warning "当前Ruby版本 ($RUBY_VERSION) 可能太旧，建议升级到Ruby 2.7+"
            print_warning "可以使用Homebrew安装新版本: brew install ruby"
            print_warning "然后确保/opt/homebrew/bin在PATH环境变量中"
        fi
        
        if [ ! -d "ios/Pods" ]; then
            print_info "安装CocoaPods依赖..."
            cd ios
            
            # 获取合适的bundle命令
            BUNDLE_CMD=$(get_bundle_cmd)
            print_info "使用bundle命令: $BUNDLE_CMD"
            
            # 尝试pod install，失败时提示手动修复
            if ! $BUNDLE_CMD exec pod install --no-repo-update; then
                print_error "CocoaPods安装失败，请检查RubyGems源配置"
                print_info "常见问题解决步骤："
                print_info "1. 检查网络连接和代理设置"
                print_info "2. 验证RubyGems源配置："
                print_info "   - 当前Gemfile源: $(head -1 ../Gemfile)"
                print_info "   - 系统gem源: $(gem source -l 2>/dev/null | head -2 | tail -1 || echo '未配置')"
                print_info "3. 使用阿里云镜像重试："
                print_info "   cd <your-workspace>/frontend"
                print_info "   sed -i '' 's|^source .*|source \"https://mirrors.aliyun.com/rubygems/\"|' Gemfile"
                print_info "   gem sources --add https://mirrors.aliyun.com/rubygems/"
                print_info "   bundle install"
                print_info "   cd ios && bundle exec pod install"
                print_info "4. 或使用--verbose选项查看详细错误：bundle exec pod install --verbose"
                exit 1
            fi
            
            cd ..
            print_success "iOS依赖安装完成"
        else
            print_info "iOS依赖已安装"
        fi
    else
        print_warning "非macOS系统，跳过iOS依赖检查"
    fi
}

check_ios_device_signing_ready() {
    local identity_count
    identity_count=$(security find-identity -v -p codesigning 2>/dev/null | awk '/valid identities found/ {print $1}')
    identity_count="${identity_count:-0}"

    if [ "$identity_count" = "0" ]; then
        print_error "当前Mac上没有可用的iOS代码签名身份，无法直接命令行安装到真机"
        print_info "请先在 Xcode 中完成一次签名初始化："
        print_info "1. 打开 ios/frontend.xcworkspace"
        print_info "2. 选择 target frontend -> Signing & Capabilities"
        print_info "3. 勾选 Automatically manage signing"
        print_info "4. Team 选择你的 Apple ID 对应的 Personal Team"
        print_info "5. 保持 Bundle Identifier 为唯一值（当前默认值为 com.quickstart.template.frontend）"
        print_info "6. 连接 iPhone 后，在 Xcode 里点一次 Run，让证书和 profile 自动生成"
        exit 1
    fi
}

check_ios_simulator_runtime_ready() {
    local simulator_sdk_version

    simulator_sdk_version=$(xcodebuild -showsdks 2>/dev/null | awk '/Simulator - iOS / {print $4; exit}')

    if [ -z "$simulator_sdk_version" ]; then
        print_error "未检测到 iOS Simulator SDK，Xcode 安装状态异常"
        print_info "可先执行: xcodebuild -runFirstLaunch"
        exit 1
    fi

    if ! xcrun simctl list runtimes available 2>/dev/null | grep -Fq "iOS ${simulator_sdk_version} "; then
        print_error "当前 Xcode 需要 iOS ${simulator_sdk_version} Simulator runtime，但本机未安装"
        print_info "可执行: xcodebuild -downloadPlatform iOS"
        exit 1
    fi
}

# 启动Metro开发服务器
start_metro() {
    print_info "启动Metro开发服务器..."
    print_warning "注意: Metro服务器将在前台运行，按Ctrl+C停止"
    echo ""
    write_runtime_config
    stop_existing_metro
    npx react-native start --reset-cache
}

# 启动iOS开发环境
start_ios() {
    if [ "$(uname)" != "Darwin" ]; then
        print_error "iOS开发需要macOS系统"
        exit 1
    fi
    
    print_info "启动iOS开发环境..."
    write_runtime_config
    sync_ios_wechat_config
    start_api_proxy_background
    
    # 检查Xcode
    if ! xcodebuild -version >/dev/null 2>&1; then
        print_error "Xcode未安装或未配置"
        exit 1
    fi

    check_ios_simulator_runtime_ready
    
    # 启动iOS模拟器
    print_info "启动React Native iOS应用..."
    start_metro_background
    
    # 确保使用正确的bundle命令，避免bundler版本冲突
    BUNDLE_CMD=$(get_bundle_cmd)
    print_info "使用bundle命令: $BUNDLE_CMD"
    
    # 设置环境变量确保使用正确的Ruby和bundler，避免交互式安装
    export PATH="/opt/homebrew/Cellar/ruby/4.0.1/bin:$PATH"
    export CI=true
    export BUNDLE_SILENCE_ROOT_WARNING=1
    export BUNDLE_APP_CONFIG=/dev/null

    local simulator_udid
    simulator_udid="${IOS_SIMULATOR_UDID:-$(get_booted_ios_simulator_udid)}"

    if [ -z "$simulator_udid" ]; then
        simulator_udid="$(get_first_available_ios_simulator_udid)"
    fi

    if [ -z "$simulator_udid" ]; then
        print_error "未找到可用的 iOS 模拟器"
        exit 1
    fi

    ensure_ios_simulator_booted "$simulator_udid"

    local simulator_name
    simulator_name="$(get_ios_simulator_name_by_udid "$simulator_udid")"
    if [ -n "$simulator_name" ]; then
        print_info "目标模拟器: $simulator_name ($simulator_udid)"
    else
        print_info "目标模拟器 UDID: $simulator_udid"
    fi

    npx react-native run-ios --udid "$simulator_udid"
}

start_device() {
    if [ "$(uname)" != "Darwin" ]; then
        print_error "iPhone真机开发需要macOS系统"
        exit 1
    fi

    print_info "启动iPhone真机开发环境..."
    write_runtime_config
    sync_ios_wechat_config

    if ! xcodebuild -version >/dev/null 2>&1; then
        print_error "Xcode未安装或未配置"
        exit 1
    fi

    check_ios_device_signing_ready

    local resolved_device_name
    resolved_device_name="$TARGET_DEVICE_NAME"

    resolved_device_name="$(resolve_target_ios_device_name "$resolved_device_name")"

    if [ -z "$resolved_device_name" ]; then
        print_error "未检测到已连接的iPhone真机"
        print_info "可用真机列表:"
        print_available_ios_devices || true
        print_info "如设备已连接，请先在Xcode中确认已信任设备并可被识别"
        print_info "可在 Xcode -> Window -> Devices and Simulators 中检查"
        print_info "也可以显式指定设备名: ./start.sh device local \"你的iPhone名称\""
        exit 1
    fi

    print_info "目标设备: $resolved_device_name"
    local device_run_log
    device_run_log="/tmp/social-app-device-run.log"
    rm -f "$device_run_log" 2>/dev/null || true

    if [ -n "$PROXY_TARGET" ]; then
        # Device-local mode still embeds the JS bundle, but it needs a relay so the
        # phone can reach the host backend over the LAN without exposing port 8080.
        start_api_proxy_background
    fi

    if [ "$APP_ENV" = "remote" ]; then
        print_info "远端真机联调使用 Release 构建，不依赖 Metro"
        npx react-native run-ios --mode Release --no-packager --device "$resolved_device_name" 2>&1 | tee "$device_run_log"
    else
        print_info "真机本地联调使用 Release 构建，并通过局域网代理访问本机后端"
        npx react-native run-ios --mode Release --no-packager --device "$resolved_device_name" 2>&1 | tee "$device_run_log"
    fi

    if grep -q "Could not find a physical device" "$device_run_log"; then
        print_error "Xcode 未找到匹配的真机: $resolved_device_name"
        print_info "可用真机列表:"
        print_available_ios_devices || true
        exit 1
    fi

    if grep -q "success Installed the app on the device." "$device_run_log"; then
        if launch_ios_app_on_device "$resolved_device_name"; then
            print_success "已在真机上重新启动最新构建的 App"
        else
            print_warning "App 已安装到真机，但自动拉起失败，请在手机上手动点开 frontend"
            print_info "devicectl 日志: /tmp/social-app-devicectl-launch.log"
        fi
    fi
}

# 启动Android开发环境
start_android() {
    print_info "启动Android开发环境..."
    write_runtime_config
    start_metro_background
    
    # 检查Android环境
    if ! command -v adb >/dev/null 2>&1; then
        print_warning "adb未找到，请确保Android SDK已安装"
    fi
    
    # 检查Android设备/模拟器
    if ! adb devices | grep -q "device$"; then
        print_warning "未找到Android设备或模拟器，请先启动模拟器"
    fi
    
    # 启动Android应用
    print_info "启动React Native Android应用..."
    npx react-native run-android
}

# 主函数
main() {
    print_info "=== 前端开发启动脚本 ==="
    print_info "项目目录: $(pwd)"
    
    # 环境检查
    check_environment
    
    # 安装依赖
    install_dependencies
    
    # 解析参数
    PLATFORM="${1:-ios}"
    APP_ENV="${2:-local}"
    CONFIG_PLATFORM="$PLATFORM"
    TARGET_DEVICE_NAME=""

    if [[ "$PLATFORM" == "local" || "$PLATFORM" == "remote" ]]; then
        APP_ENV="$PLATFORM"
        if [ -n "${2:-}" ]; then
            PLATFORM="device"
            CONFIG_PLATFORM="device"
            TARGET_DEVICE_NAME="$2"
        else
            PLATFORM="ios"
            CONFIG_PLATFORM="ios"
        fi
    fi

    if [ "$PLATFORM" = "device" ]; then
        if [ -n "${3:-}" ]; then
            case "$3" in
                ios|android|device)
                    CONFIG_PLATFORM="$3"
                    TARGET_DEVICE_NAME="${4:-}"
                    ;;
                *)
                    CONFIG_PLATFORM="device"
                    TARGET_DEVICE_NAME="$3"
                    ;;
            esac
        fi
    else
        CONFIG_PLATFORM="${3:-$PLATFORM}"
        TARGET_DEVICE_NAME="${4:-}"
    fi
    if [ "$PLATFORM" = "metro" ] && [ "$CONFIG_PLATFORM" = "metro" ]; then
        CONFIG_PLATFORM="ios"
    fi
    resolve_runtime_config
    
    case "$PLATFORM" in
        ios)
            install_ios_deps
            start_ios
            ;;
        device)
            install_ios_deps
            start_device
            ;;
        android)
            start_android
            ;;
        metro)
            start_metro
            ;;
        *)
            print_error "无效的平台: $PLATFORM"
            print_info "用法: $0 [ios|android|device|metro] [local|remote] [ios|android|device] [设备名]"
            exit 1
            ;;
    esac
    
    print_success "启动完成!"
}

# 执行主函数
main "$@"
