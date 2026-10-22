# 用户端网页

采用 Vue 3、TypeScript 严格模式、Vite、Element Plus、Vue Router 4、Pinia 和 Axios，版本与管理端保持一致。

当前已实现登录／注册、在线点餐、我的订单、收货地址、我的及登录后的公共布局。根路径 `/` 重定向到 `/menu`；路由守卫读取 Cookie 中的 `token`，未登录进入登录页。登录页桌面端左右分栏为 6:4，左侧铺满背景图，右侧表单顶部 logo 水平居中，窄屏仅展示表单。背景图位于 `src/assets/images/login/background.webp`，保留 1402×1122 分辨率并压缩为 WebP。

登录后的公共布局包含始终显示的白色顶部导航栏和浅蓝色内容区。导航栏左侧复用登录 logo，点击返回 `/menu`；中间四个入口为“在线点餐” `/menu`、“我的订单” `/orders`、“收货地址” `/addresses` 和“我的” `/profile`，选中的板块显示蓝色下划线。右侧显示头像和“你好，用户昵称”，点击进入 `/profile`。小屏幕下导航栏分为两行，保留全部入口。

“在线点餐”页面包含蓝色渐变宣传区（右侧使用 `src/assets/images/menu/background.webp`，保留 1832×859 分辨率，约 310 KB；高清原图保存在同目录 `background-original.png`）、复用管理端无文字 logo 的店铺信息、营业状态、配送与打包费及选中地址入口。“看看今天吃什么”滚动至包含分类选项卡的位置，避开固定导航栏。支持全部餐点及后端返回的菜品／套餐分类，列表在前端分页，每页六件、桌面三列两行；分类切换回到第一页，仅超过六件时展示分页，不提供搜索框。点击图片或名称可查看详情，菜品可按口味组分别单选，套餐显示包含菜品及份数。

右侧餐袋在桌面滚动至点餐位置后吸顶，商品列表内部可滚动；小屏放在列表下方。支持按商品和口味加减、确认清空、服务端购物车恢复，以及空餐袋与读取失败重试。再来一单产生的同商品同口味重复条目合并展示，逐件操作后重新读取服务端结果。餐品按单价乘数量计价，打包费每件 1 元、配送费每单 6 元，空餐袋全部为零。店铺休息或营业状态读取失败时禁止加购与结算，提交前重新确认营业状态。

确认订单弹窗支持地址下拉选择、地址管理跳转、立即配送或未来送达时间、按餐量提供／无需餐具／1–10 套餐具以及最多 100 字备注。下单只提交配送及备注信息，金额由后端计算；成功后清空购物车，使用后端返回金额进入共享模拟支付弹窗。暂不支付时保留未付款订单和继续支付入口，也可从“我的订单”付款；成功后显示支付成功弹窗，可继续点餐或查看订单。购物车写入期间禁用重复提交，读取失败后先恢复数据再允许继续操作。

“我的订单”页面包含全部订单、待付款、进行中、已完成、已取消五个选项卡，进行中查询状态 2、3、4。桌面三列两行，每页固定六条，仅总数超过六条时显示 Element Plus 分页，不提供每页条数切换。卡片显示下单时间、订单号、状态、菜品图片与名称、件数、总金额及地址；历史完整地址仅在与地址簿完整匹配时省略省市区，其余保留订单快照。详情弹窗显示进度、菜品及口味数量、每行小计、打包费、配送费、订单合计、收货及配送信息、餐具、备注、支付状态和取消原因／时间。

列表与详情共用操作规则：待付款可支付／取消，待接单可催单／取消，其余状态可联系商家，所有状态可再来一单。支付使用当前后端的模拟支付流程，弹窗明确说明不会实际扣款；取消前要求确认，成功后重新读取列表与详情。联系商家显示 `138 0000 0000` 演示手机号。再来一单调用后端追加购物车，同步 Pinia 购物车后进入 `/menu`，保留原有商品。购物车刷新失败不会重复调用再来一单接口。

“收货地址”页面在桌面以双列卡片展示地址标签、默认标记、详细地址、省市区、收货人及手机号，小屏幕使用单列。支持新增、编辑回显、确认删除和设置默认地址；设置默认成功后立即选中。配送选择与默认标记独立，选择保存在 Pinia 中，切换页面保留，刷新后优先选择默认地址，退出或切换账号时清空。新增／编辑弹窗包含收货人、手机号、手工输入的省市区、详细地址及家／公司／学校／其它标签，校验必填项和手机号，背景虚化且小屏可滚动。弹窗不提供默认地址复选框，默认地址通过卡片设置。兼容旧小程序的数字标签；修改地区名称时清除不再匹配的旧地区编码。

“我的”页面显示头像昵称、基本信息和账号安全三个白色板块。基本信息包含昵称、姓名、性别、手机号、身份证号和地址管理入口。编辑资料弹窗按头像、昵称、真实姓名、性别、手机号、身份证号的顺序回显，选填信息可以清空；保存后同步更新页面和导航栏。头像支持 PNG/JPEG，最大 1 MB，上传后需保存资料才会绑定到用户。修改密码弹窗校验旧密码、新密码格式与两次输入一致性；修改成功保持当前登录状态。两个弹窗均支持背景虚化和小屏滚动。退出登录会调用后端并清除 Cookie 和 Pinia 会话。

账号长度为 3–32 位，由字母和数字组成且以字母开头；密码至少 6 位，仅限字母和数字。注册由前端校验确认密码，后端只接收 `account`、`password`。注册成功后返回登录选项卡，保留账号并清空密码。

## 本地启动

Node.js 版本要求与管理端相同：`^22.22.2 || ^24.15.0 || >=26.0.0`。

```sh
npm ci
npm run dev
```

默认访问 `http://localhost:5174`，避免与管理端的 5173 端口冲突。先按根目录 README 启动后端；Vite 将 `/api/*` 代理至 `http://localhost:8080/user/*`。需要修改后端地址时，在 `.env.development.local` 中设置 `VITE_API_PROXY_TARGET` 并重启。

登录请求为 `POST /api/user/login`，注册请求为 `POST /api/user/register`。登录成功后 Cookie 保存 `token`，有效期与后端 `user-ttl` 一致（2 小时），用户资料保存在 Pinia；刷新或直接打开已登录页面时，通过 `GET /api/user/info` 恢复昵称和头像。未设置头像或头像加载失败时显示默认头像。统一请求从 Cookie 读取凭证并设置 `authentication` 请求头；401 清除会话并返回登录页。Vite 同时将 `/images/*` 代理到后端，为相对地址的用户头像提供访问。

个人资料相关接口：`GET /api/user/info` 返回当前用户的 `id`、`account` 及资料；`PUT /api/user/update` 保存头像、昵称等资料，账号不可编辑；`POST /api/common/upload` 使用 multipart `file` 上传头像；`PUT /api/user/editPassword` 提交旧密码和新密码；`POST /api/user/logout` 退出登录。

地址相关接口：`GET /api/addressBook/list` 查询列表，`GET /api/addressBook/{id}` 读取详情，`POST /api/addressBook` 新增，`PUT /api/addressBook` 编辑，`PUT /api/addressBook/default` 以 `{ id }` 设置默认，`DELETE /api/addressBook?id=...` 删除。新增接口返回空数据，保存成功后重新查询列表；新增／编辑不提交 `isDefault`，保留后端现有的默认地址规则。

订单相关接口：`GET /api/order/historyOrdersByStatusList?pageNum=1&statusList=2,3,4` 分页筛选，查询全部时省略 `statusList`；`GET /api/order/orderDetail/{id}` 读取详情，`PUT /api/order/payment` 以订单号及 `payMethod: 1` 模拟支付，`PUT /api/order/cancel/{id}` 取消，`GET /api/order/reminder/{id}` 催单，`POST /api/order/repetition/{id}` 再来一单。`GET /api/shoppingCart/list` 读取加入商品后的真实购物车。

点餐接口使用 `GET /api/shop/status`、`GET /api/category/list`、`GET /api/dish/list?categoryId=…`、`GET /api/setmeal/list?categoryId=…` 和 `GET /api/setmeal/dish/{id}`。购物车使用 `GET /api/shoppingCart/list`、`POST /api/shoppingCart/add`、`POST /api/shoppingCart/sub` 和 `DELETE /api/shoppingCart/clean`；提交订单使用 `POST /api/order/submit`。用户授权修正了后端购物车查询条件：带商品 ID 的空口味操作只匹配 SQL NULL 口味，空字符串仍精确匹配，不传商品 ID 的列表查询继续返回全部口味。

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

验证个人资料的真实保存、头像上传、密码修改及重新登录：

```sh
E2E_BACKEND=1 npm run test:e2e -- e2e/profile.backend.spec.ts --project=chromium
```

该测试会创建一个 `E2eProfile` 开头的独立测试用户及测试头像，不修改已有用户资料。

验证收货地址的真实新增、编辑、设置默认、刷新回显和删除：

```sh
E2E_BACKEND=1 npm run test:e2e -- e2e/addresses.backend.spec.ts --project=chromium
```

该测试创建 `E2eAddress` 开头的独立测试用户，并在流程结束时删除本次新增的地址，不修改已有用户地址。

验证订单分页、详情、模拟支付、催单、取消及购物车追加：

```sh
E2E_BACKEND=1 npm run test:e2e -- e2e/orders.backend.spec.ts --project=chromium
```

该测试创建 `E2eOrder` 开头的独立账号及七笔订单，使用现有在售菜品；结束时取消该账号尚未接单的测试订单并清空其购物车，不修改其他用户数据。

验证点餐口味选择、空口味隔离增减、结算、未付款恢复和模拟支付：

```sh
E2E_BACKEND=1 npm run test:e2e -- e2e/menu.backend.spec.ts --project=chromium
```

该测试创建 `E2eMenu` 开头的独立账号，使用现有在售菜品，不操作已有用户数据。结束时取消测试订单、清空测试购物车并删除测试地址。需先启动营业中的后端，并重新加载购物车空口味查询修复。

使用已有账号只读验证公共布局、导航与刷新后的用户信息：

```sh
E2E_USER_ACCOUNT=demo01 E2E_USER_PASSWORD=123456 npm run test:e2e -- e2e/layout.backend.spec.ts --project=chromium
```

`npm run build` 输出到 `dist/`。生产环境需将 `/api/*` 代理到后端 `/user/*`，将 `/images/*` 代理到后端同名路径，并配置 history 路由回退到 `index.html`。管理端和用户端应使用不同主机名，以隔离各自的 `token` Cookie。
