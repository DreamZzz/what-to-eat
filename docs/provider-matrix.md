# Provider Matrix

| 能力 | 环境变量 | 默认值 | 备选值 | 说明 |
| --- | --- | --- | --- | --- |
| 存储 | `APP_MEDIA_STORAGE_PROVIDER` | `local` | `oss` | 本地默认可跑通，生产可切 OSS |
| 地图 | `APP_MAP_PROVIDER` | `disabled` | `amap` | 未配置时地点搜索返回 503 |
| 邮件 | `APP_AUTH_PASSWORD_RESET_PROVIDER` | `log` | `mail` | 本地记录日志，生产走 SMTP |
| 短信 | `APP_AUTH_SMS_PROVIDER` | `log` | `aliyun` | 本地记录日志，生产走阿里云短信 |
| 搜索 | `APP_SEARCH_PROVIDER` | `database` | `elasticsearch` | ES 不可用时可退数据库 |
