# 碧蓝外卖用户端

用户端采用 Vue 3、Composition API 和 TypeScript 严格模式，提供登录／注册、在线点餐、我的订单、收货地址和个人资料五个页面。支付目前使用模拟流程。项目整体功能与后端启动步骤见 [项目 README](../README.md)。

## 主要技术栈及版本

版本以当前 [package-lock.json](package-lock.json) 为准。

| 技术 / 组件 | 版本 | 主要用途 |
| --- | --- | --- |
| Vue | 3.5.44 | 页面与组件开发 |
| TypeScript | 6.0.3 | 类型定义与严格类型检查 |
| Vite | 8.3.4 | 开发服务与构建 |
| Element Plus | 2.14.7 | 表单、弹窗、分页等 UI 组件 |
| Vue Router | 4.6.4 | 页面路由与登录守卫 |
| Pinia | 4.1.0 | 登录资料、购物车与地址选择状态管理 |
| Axios | 1.20.0 | HTTP 请求与统一错误处理 |

## 目录结构

```text
user-frontend/
├── src/
│   ├── api/                                  # 按业务封装接口与请求、响应类型
│   ├── assets/
│   │   ├── images/                           # 品牌、登录页及点餐宣传图片
│   │   └── styles/                           # 全局主题及共用弹窗样式
│   ├── components/                           # 地址表单、支付弹窗、头像及图片共用组件
│   ├── layouts/
│   │   ├── components/                       # 顶部导航与用户入口
│   │   └── UserLayout.vue                    # 用户端公共布局
│   ├── router/                               # 页面路由与登录守卫
│   ├── stores/                               # Pinia 会话、购物车与地址选择状态
│   ├── types/                                # 通用接口类型
│   ├── utils/                                # 请求封装、Cookie、地址与计价工具
│   ├── views/
│   │   ├── login/                            # 账号密码登录与注册
│   │   ├── menu/                             # 分类点餐、购物车与下单流程
│   │   │   └── components/                   # 餐点卡片、详情口味、餐袋与结算弹窗
│   │   ├── order/                            # 订单筛选、分页及操作逻辑
│   │   │   └── components/                   # 订单卡片、操作按钮与详情弹窗
│   │   ├── address/                          # 收货地址管理与默认地址设置
│   │   │   └── components/                   # 地址卡片与配送地址选择入口
│   │   └── profile/                          # 个人资料与账号安全
│   │       └── components/                   # 头像上传、资料编辑与密码修改弹窗
│   ├── App.vue                               # 根组件
│   └── main.ts                               # 应用入口
├── e2e/                                      # 浏览器流程测试
│   └── helpers/                              # 模拟接口及测试辅助代码
├── index.html                                # HTML 入口与浏览器图标
├── package.json                              # 依赖与运行脚本
├── package-lock.json                         # 依赖版本锁定
├── vite.config.ts                            # 构建、路径别名与开发代理
├── vitest.config.ts                          # 单元与组件测试配置
└── playwright.config.ts                      # 浏览器测试配置
```

单元与组件测试位于各模块的 `__tests__/` 目录，浏览器测试位于 `e2e/`；分别使用 Vitest、Vue Test Utils 和 Playwright。

## 本地运行

需要 Node.js `^22.22.2 || ^24.15.0 || >=26.0.0`，并启动后端。在本目录执行：

```sh
npm ci
npm run dev
```

开发服务固定使用 `http://localhost:5174`，端口被占用时直接报错。Vite 将 `/api/*` 代理到后端 `/user/*`，将 `/images/*` 代理到后端同名路径。默认后端地址为 `http://localhost:8080`；需要调整时，在 `.env.development.local` 中设置后重启：

```dotenv
VITE_API_PROXY_TARGET=http://localhost:8080
```

登录凭证保存在 `user_token` Cookie 中，请求头使用 `authentication`；管理端使用独立的 `admin_token` Cookie，两端可在同一主机名的不同端口下独立登录和退出。原有登录状态需重新登录一次。

## 验证

```sh
npm run test:unit -- --run
npm run build
npm run lint
npx playwright install chromium
npm run test:e2e -- --project=chromium
```

默认浏览器测试模拟后端接口。设置 `E2E_BACKEND=1` 可启用真实后端联调，会创建独立测试账号及相关数据；点餐测试需要店铺处于营业状态。使用本机 Chrome 时可设置 `PLAYWRIGHT_CHROMIUM_CHANNEL=chrome`。

## 构建部署

`npm run build` 输出到 `dist/`。生产环境使用 HTTPS，将 `/api/*` 代理到后端 `/user/*`，将 `/images/*` 代理到后端同名路径。

项目使用 History 路由，Nginx 静态页面的 `location /` 中需设置 `try_files $uri $uri/ /index.html;`，支持直接访问和刷新页面。
