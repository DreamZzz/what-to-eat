# Frontend Guide

## 结构

- `src/app`: app shell、provider、navigation、runtime config
- `src/features`: auth / meal / media / profile
- `src/shared`: shared api client 与共享导出

## 常用命令

```bash
npm install
npm run lint
npm test -- --runInBand
./start.sh local
./start.sh remote
```

## 设计原则

- 业务能力从 `features` 暴露，不从 `screens` 直接耦合。
- `src/services/api.js` 是兼容入口；新代码优先从 feature API 或 `shared/api/client` 取能力。
- 运行时配置统一收敛到 `src/app/config/*`。
- 当前前端主链路围绕登录、首页输入、流式菜谱、按需补图/补步骤和收藏展开，不再保留社区/搜索/地点页面。
