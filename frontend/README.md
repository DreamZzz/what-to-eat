# Frontend Guide

## 结构

- `src/app`: app shell、provider、navigation、runtime config
- `src/features`: auth / content / comments / location / media / profile / search
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
- `src/config/*` 仍保留兼容导出；真实实现位于 `src/app/config/*`。
