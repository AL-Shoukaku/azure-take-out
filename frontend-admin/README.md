# 苍穹外卖管理端

使用 Vue 3、TypeScript 严格模式、Vite、Element Plus、Vue Router 4、Pinia 和 Axios。目前实现登录页面，工作台暂时只显示“完成登录”。登录页沿用旧版背景图、Logo 和布局。

## 本地运行

需要 Node.js `^22.18.0` 或 `>=24.12.0`，并启动 `sky-take-out` 后端（默认 `http://localhost:8080`）。

```sh
npm install
npm run dev
```

打开终端显示的地址。`/` 重定向到 `/dashboard`；未登录时进入 `/login`。表单不预填账号和密码，支持回车提交。

浏览器请求 `/api/employee/login`，Vite 将 `/api` 转发为后端的 `/admin`。需要连接其他后端时，在 `.env.development.local` 中设置以下内容，然后重启开发服务：

```dotenv
VITE_API_PROXY_TARGET=http://localhost:8080
```

登录成功后将 `token` 存入 Cookie（根路径、SameSite=Lax、HTTPS 下启用 Secure），有效期与当前后端一致，为两小时。刷新后从 Cookie 恢复登录状态，后续请求使用 `token` 请求头。业务错误展示后端原始提示；401 会清除会话并返回登录页。

## 验证

```sh
npm run test:unit -- --run
npm run build
npm run lint
npx playwright install chromium
npm run test:e2e -- --project=chromium
```

单元测试覆盖接口数据、原始业务报错、网络错误、会话和路由守卫；浏览器测试覆盖表单校验、登录中状态、重复提交、失败重试、刷新保持状态和窄屏布局。默认浏览器测试拦截登录接口，不依赖后端。

如需使用本机安装的 Chrome，可设置 `PLAYWRIGHT_CHROMIUM_CHANNEL=chrome`。

连接真实后端的浏览器测试需要额外提供账号和密码：

```sh
E2E_ADMIN_USERNAME=你的账号 E2E_ADMIN_PASSWORD=你的密码 npm run test:e2e -- --project=chromium
```

未设置这两个环境变量时，真实后端测试会跳过。真实测试执行登录和刷新流程。

## 构建部署

`npm run build` 输出到 `dist/`。部署服务器需将 `/api/*` 代理到后端 `/admin/*`，并将前端路由回退到 `index.html`，以支持直接访问和刷新 `/login`、`/dashboard`。
