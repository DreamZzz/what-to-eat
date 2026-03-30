#!/bin/bash
# OSS访问权限诊断脚本

set -e

echo "=== OSS访问权限诊断脚本 ==="
echo

# 加载环境变量
if [ -f .env ]; then
    echo "加载.env文件中的环境变量..."
    source .env
fi

BUCKET_NAME="${ALIYUN_OSS_BUCKET_NAME:-test-ai-redbook}"
ENDPOINT="${ALIYUN_OSS_ENDPOINT:-oss-cn-beijing.aliyuncs.com}"

echo "Bucket: $BUCKET_NAME"
echo "Endpoint: $ENDPOINT"
echo

# 测试1: 验证根目录可访问性（用户提供的测试文件）
echo "=== 测试1: 验证根目录可访问性 ==="
TEST_FILE="testicon.png"
ROOT_URL="https://$BUCKET_NAME.$ENDPOINT/$TEST_FILE"
echo "测试URL: $ROOT_URL"
echo "curl测试:"
curl -s -I "$ROOT_URL" | head -10
echo

# 测试2: 验证uploads文件夹访问权限
echo "=== 测试2: 验证uploads文件夹访问权限 ==="
UPLOADS_URL="https://$BUCKET_NAME.$ENDPOINT/uploads/"
echo "测试URL: $UPLOADS_URL"
echo "curl测试:"
curl -s -I "$UPLOADS_URL" | head -10
echo

# 测试3: 测试带不同Headers的访问
echo "=== 测试3: 测试不同Content-Type的访问 ==="
echo "测试1: 默认Accept"
curl -s -I -H "Accept: */*" "$ROOT_URL" | grep -E "HTTP|Content-Type|Content-Length"
echo

echo "测试2: 图片Accept"
curl -s -I -H "Accept: image/*" "$ROOT_URL" | grep -E "HTTP|Content-Type|Content-Length"
echo

# 测试4: 生成诊断报告
echo "=== 测试4: OSS权限诊断 ==="
echo "1. 检查Bucket公共读权限:"
echo "   - 登录OSS控制台 -> $BUCKET_NAME -> 权限管理 -> 公共读写"
echo "   - 确保设置为'公共读'"
echo

echo "2. 检查CORS配置:"
echo "   - OSS控制台 -> $BUCKET_NAME -> 数据安全 -> 跨域设置"
echo "   - 确保已添加CORS规则，允许所有来源(*)"
echo

echo "3. 检查防盗链(Referer)设置:"
echo "   - OSS控制台 -> $BUCKET_NAME -> 数据安全 -> 防盗链"
echo "   - 确保允许空Referer或已添加白名单"
echo

echo "4. 检查Bucket Policy:"
echo "   - OSS控制台 -> $BUCKET_NAME -> 权限管理 -> Bucket Policy"
echo "   - 确保没有限制uploads/路径的访问"
echo

# 测试5: 建议的临时解决方案
echo "=== 临时解决方案 ==="
echo "如果uploads/文件夹无法访问，但根目录可以访问:"
echo "1. 临时修改代码，上传到根目录:"
cat << 'EOF'
在FileStorageService.java中修改第77行:
// 原代码:
String objectKey = ossProperties.getFolder() + fileName;

// 修改为:
String objectKey = fileName;  // 上传到根目录测试
EOF
echo

echo "2. 或者在OSS控制台设置uploads/文件夹权限:"
echo "   - 在文件管理界面，右键uploads/文件夹"
echo "   - 选择'设置权限' -> '公共读'"
echo

echo "=== 诊断完成 ==="
echo "建议:"
echo "1. 先运行此脚本检查当前权限状态"
echo "2. 如果测试1通过但测试2失败，说明是文件夹权限问题"
echo "3. 根据诊断结果采取相应措施"