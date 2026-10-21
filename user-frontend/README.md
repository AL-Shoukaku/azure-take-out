# 用户端网页

采用 Vue 3、TypeScript 严格模式、Vite、Element Plus、Vue Router 4、Pinia 和 Axios，版本与管理端保持一致。

当前实现登录／注册页 `/login` 和登录后的公共布局。根路径 `/` 重定向到 `/menu`；路由守卫读取 Cookie 中的 `token`，未登录进入登录页。登录页桌面端左右分栏为 6:4，左侧铺满背景图，右侧表单顶部 logo 水平居中，窄屏仅展示表单。背景图位于 `src/assets/images/login/background.webp`，保留 1402×1122 分辨率并压缩为 WebP。

登录后的公共布局包含始终显示的白色顶部导航栏和浅蓝色内容区。导航栏左侧复用登录 logo，点击返回 `/menu`；中间四个入口为“在线点餐” `/menu`、“我的订单” `/orders`、“收货地址” `/addresses` 和“我的” `/profile`，选中的板块显示蓝色下划线。右侧显示头像和“你好，用户昵称”，点击进入 `/profile`。四个页面目前只显示“当前是 xx 板块”。小屏幕下导航栏分为两行，保留全部入口。

账号长度为 3–32 位，由字母和数字组成且以字母开头；密码至少 6 位，仅限字母和数字。注册由前端校验确认密码，后端只接收 `account`、`password`。注册成功后返回登录选项卡，保留账号并清空密码。

## 本地启动

Node.js 版本要求与管理端相同：`^22.22.2 || ^24.15.0 || >=26.0.0`。

```sh
npm ci
npm run dev
```

默认访问 `http://localhost:5174`，避免与管理端的 5173 端口冲突。先按根目录 README 启动后端；Vite 将 `/api/*` 代理至 `http://localhost:8080/user/*`。需要修改后端地址时，在 `.env.development.local` 中设置 `VITE_API_PROXY_TARGET` 并重启。

登录请求为 `POST /api/user/login`，注册请求为 `POST /api/user/register`。登录成功后 Cookie 保存 `token`，有效期与后端 `user-ttl` 一致（2 小时），用户资料保存在 Pinia；刷新或直接打开已登录页面时，通过 `GET /api/user/info` 恢复昵称和头像。未设置头像或头像加载失败时显示默认头像。统一请求从 Cookie 读取凭证并设置 `authentication` 请求头；401 清除会话并返回登录页。Vite 同时将 `/images/*` 代理到后端，为相对地址的用户头像提供访问。

管理端和用户端使用相同的 Cookie 名称。在本地同时使用两端时，请分别通过 `127.0.0.1` 和 `localhost` 访问，以免浏览器在同一主机的不同端口间共享 Cookie。

## 验证

```sh
npm run test:unit -- --run
npm run build
npm run lint
npx playwright install chromium
npm run test:e2e -- --project=chromium
```

默认浏览器测试使用模拟接口。连接真实后端执行完整注册、重复账号、密码错误、登录与凭证验证：

```sh
E2E_BACKEND=1 npm run test:e2e -- e2e/login.backend.spec.ts --project=chromium
```

真实后端测试会创建一个 `E2eUser` 开头的独立测试用户。需先启动后端及数据库。

使用已有账号只读验证公共布局、导航与刷新后的用户信息：

```sh
E2E_USER_ACCOUNT=demo01 E2E_USER_PASSWORD=123456 npm run test:e2e -- e2e/layout.backend.spec.ts --project=chromium
```

`npm run build` 输出到 `dist/`。生产环境需将 `/api/*` 代理到后端 `/user/*`，将 `/images/*` 代理到后端同名路径，并配置 history 路由回退到 `index.html`。管理端和用户端应使用不同主机名，以隔离各自的 `token` Cookie。
