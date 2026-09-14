# sky-delivery-plus

外卖平台开发工作区，包含 **后端服务**、**管理端前端**、**微信小程序（用户端）** 三端。

> 项目基于黑马程序员《苍穹外卖》教学项目整理，此处为个人学习与二次开发用的工作区版本。
> 课程原始资料（讲义 / PPT / 产品原型 / 接口文档）在 `docs/`，未纳入版本管理。

## 技术栈

| 模块 | 技术 | 端口 |
| --- | --- | --- |
| 后端 | Spring Boot 2.7.3、MyBatis、Druid、Redis、WebSocket、JWT、POI、Knife4j | 8080 |
| 管理端前端 | Vue 2 + TypeScript + Element UI | 8081（dev server） |
| 微信小程序 | uni-app | — |
| 运行环境 | Nginx 1.20.2（托管打包好的管理端 + 反向代理后端） | 80 |
| 数据库 | MySQL 8，库名 `sky_take_out` | 3306 |
| 缓存 | Redis，`database` 为 **10** | 6379 |

## 目录结构

```
sky-delivery-plus/
├── sky-take-out/                  # 后端（Maven 多模块）—— day01 骨架版，在此开发
│   ├── sky-common/                # 常量、工具类、自定义异常、统一返回结果
│   ├── sky-pojo/                  # entity / DTO / VO（课程已给全）
│   └── sky-server/                # 启动类、config、interceptor、员工登录；其余业务待编写
├── project-rjwm-admin-vue-ts/     # 管理端前端（Vue 2 + TypeScript）
├── mp-weixin/                     # 微信小程序（用户端）
├── db/
│   ├── sky.sql                    # 建库建表 + 初始数据
│   └── 数据库设计文档.md
├── docs/                          # 课程资料：讲义 / PPT / 接口文档 / 产品原型
│   └── reference/
│       └── sky-take-out-full/     # 完整成品版，卡住时的参考答案
└── tools/
    └── nginx-1.20.2/              # 管理端运行环境
```

后端三个模块的依赖方向是单向的：`sky-server` → `sky-pojo` / `sky-common`，业务代码只写在 `sky-server`。

## 快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p < db/sky.sql
```

建 11 张表，并写入初始数据（默认管理员账号见下方）。

### 2. 启动 Redis

后端连接的 `database` 是 **10**，不是默认的 0，确认本地 Redis 已启动且该库可用。

### 3. 启动后端

先改 `sky-take-out/sky-server/src/main/resources/application-dev.yml`：

| 配置项 | 说明 |
| --- | --- |
| `sky.datasource.host / port / username / password` | 改成你自己的 MySQL |
| `sky.redis.host / port` | 改成你自己的 Redis |
| `sky.alioss.*` | 课程共用 key，大概率已失效。图片上传要用需自备阿里云 OSS，或改写成本地存储 |
| `sky.wechat.*` | 微信支付需要真实商户号与证书文件，学习阶段可整段跳过 |

然后运行 `sky-take-out/sky-server` 下的 `com.sky.SkyApplication`，服务端口 **8080**。

### 4. 启动管理端前端（开发模式，推荐）

```bash
cd project-rjwm-admin-vue-ts
npm install
npm run serve
```

访问 http://localhost:8081。

`.env.development` 里已配好代理，**无需修改**：

```
VUE_APP_BASE_API = '/api'
VUE_APP_URL      = 'http://localhost:8080/admin'   # /api 请求代理到这里
VUE_APP_SOCKET_URL = 'ws://localhost:8080/ws/'     # 来单提醒 WebSocket
```

### 5. 启动管理端前端（Nginx 方式，跑打包产物）

`tools/nginx-1.20.2/html/sky` 下是打包好的静态资源，监听 80 端口并反向代理到 8080：

```bash
cd tools/nginx-1.20.2
./nginx.exe
```

访问 http://localhost。

> 启动前确认 `tools/nginx-1.20.2/temp/` 目录存在 —— 缺失会导致 `nginx.exe` 静默启动失败（该目录已为本工作区创建好，若误删重新 `mkdir temp` 即可）。

### 6. 微信小程序

用微信开发者工具打开 `mp-weixin/` 目录。

## 默认账号

| 端 | 账号 | 密码 |
| --- | --- | --- |
| 管理端 | `admin` | `123456` |

## 备注

- 后端 `sky-take-out/` 是 **day01 骨架版**（86 个 Java 文件）：基础设施、entity/DTO/VO、员工登录已就绪，其余业务留空。跟着课程从 **day02（员工管理、分类管理）** 开始写。
- 完整成品版（141 个 Java 文件，含全部功能）在 `docs/reference/sky-take-out-full/`，作为参考答案。两份可直接对比：

  ```bash
  diff -r sky-take-out docs/reference/sky-take-out-full -x target
  ```

  建议敲完一个模块再对一次，别边看边抄——否则跑通了但没留下东西。
- **不要混用** `资料/day12/项目完整版代码/sky-take-out/`：其包目录存在拼写错误（`nofity`，应为 `notify`），是与本版内容有出入的未清理副本。
- 管理端前端目录名 `project-rjwm-admin-vue-ts` 沿用课程原有的 Vue 后台模板工程名，与后端 `sky-take-out` 命名不一致，属正常现象。
- `docs/` 与 `tools/` 已写入 `.gitignore`：二者合计约 100MB 且非源码，保留在磁盘供查阅，但不进版本库。
