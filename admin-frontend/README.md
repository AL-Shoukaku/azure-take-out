# 碧蓝外卖管理端

管理端采用 Vue 3、Composition API 和 TypeScript 严格模式，提供登录、公共后台布局及七个管理页面。项目整体功能与后端启动步骤见 [项目 README](../README.md)。

## 主要技术栈及版本

版本以当前 [package-lock.json](package-lock.json) 为准。

| 技术 / 组件 | 版本 | 主要用途 |
| --- | --- | --- |
| Vue | 3.5.44 | 页面与组件开发 |
| TypeScript | 6.0.3 | 类型定义与严格类型检查 |
| Vite | 8.3.4 | 开发服务与构建 |
| Element Plus | 2.14.7 | 表格、表单、弹窗等 UI 组件 |
| Vue Router | 4.6.4 | 页面路由与登录守卫 |
| Pinia | 4.1.0 | 登录资料与订单消息状态管理 |
| Axios | 1.20.0 | HTTP 请求与统一错误处理 |
| ECharts | 6.1.0 | 营业额、用户、订单及销量统计图表 |

## 目录结构

```text
admin-frontend/
├── src/
│   ├── api/                                  # 按业务封装接口与请求、响应类型
│   ├── assets/
│   │   ├── images/                           # 品牌、登录页及工作台图片
│   │   ├── sounds/                           # 来单与催单提示音
│   │   ├── styles/                           # 布局及管理页面共用样式
│   │   └── main.css                          # 全局样式与蓝色主题变量
│   ├── components/                           # 分页、图片上传等共用组件
│   ├── composables/                          # 共用异步请求状态逻辑
│   ├── layouts/
│   │   ├── components/                       # 侧边栏、顶部栏及公共弹窗
│   │   └── AdminLayout.vue                   # 后台公共布局
│   ├── router/                               # 路由、菜单与登录守卫
│   ├── services/                             # 订单提醒 WebSocket 服务
│   ├── stores/                               # Pinia 会话与订单消息状态
│   ├── types/                                # 通用接口类型
│   ├── utils/                                # Axios 封装与 Cookie 操作
│   ├── views/
│   │   ├── login/                            # 登录
│   │   ├── dashboard/                        # 工作台
│   │   ├── statistics/                       # 数据统计
│   │   ├── order/                            # 订单列表、详情及处理流程
│   │   ├── product/                          # 菜品与套餐共用列表、表单和选菜弹窗
│   │   ├── category/                         # 分类管理
│   │   └── employee/                         # 员工管理
│   ├── App.vue                               # 根组件与中文 UI 配置
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

开发服务固定使用 `http://localhost:5173`，端口被占用时直接报错。Vite 将 `/api/*` 代理到后端 `/admin/*`，将 `/ws/*` 代理到同一个后端。默认后端地址为 `http://localhost:8080`；需要调整时，在 `.env.development.local` 中设置后重启：

```dotenv
VITE_API_PROXY_TARGET=http://localhost:8080
```

登录凭证保存在 `admin_token` Cookie 中，请求头使用 `token`；用户端使用独立的 `user_token` Cookie，两端登录和退出互不覆盖。

## 验证

```sh
npm run test:unit -- --run
npm run build
npm run lint
npx playwright install chromium
npm run test:e2e -- --project=chromium
```

默认浏览器测试模拟后端接口与 WebSocket。设置 `E2E_ADMIN_USERNAME` 和 `E2E_ADMIN_PASSWORD` 后，会额外执行真实后端的登录、查询、报表下载及刷新检查；增删改、上传及订单处理使用模拟接口验证。使用本机 Chrome 时可设置 `PLAYWRIGHT_CHROMIUM_CHANNEL=chrome`。

## 构建部署

`npm run build` 输出到 `dist/`。生产环境使用 HTTPS，并将 `/api/*` 代理到后端 `/admin/*`，将 `/ws/*` 代理到同一后端并支持 WebSocket Upgrade。

新版使用 History 路由，Nginx 静态页面的 `location /` 中需设置 `try_files $uri $uri/ /index.html;`，支持直接访问和刷新管理页面。
