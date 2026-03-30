# Backend Validation Guide

## 本地最小验证

```bash
cd backend
./start.sh local
```

另开终端：

```bash
./ops/scripts/smoke-api.sh
```

## provider 验证建议

- 存储：切 `APP_MEDIA_STORAGE_PROVIDER=oss` 后，验证上传返回绝对地址。
- 地图：切 `APP_MAP_PROVIDER=amap` 后，验证 `/api/locations/search`。
- 邮件：切 `APP_AUTH_PASSWORD_RESET_PROVIDER=mail` 后，验证找回密码验证码投递。
- 短信：切 `APP_AUTH_SMS_PROVIDER=aliyun` 后，验证短信登录验证码投递。
- 搜索：切 `APP_SEARCH_PROVIDER=elasticsearch` 后，验证索引重建和搜索命中。

## 回归接口

- `GET /api/auth/captcha`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/password/forgot`
- `GET /api/posts`
- `GET /api/posts/search`
- `POST /api/posts`
- `GET /api/locations/search`
