# sky-delivery-plus

## 📖 项目简介

本项目源自于黑马程序员的《苍穹外卖》教学项目。

原项目是是一个业务闭环的外卖平台，能够实现**用户登录->加入购物车->下单->支付->商家接单->骑手配送->订单完成**的完整业务流程。

- [原始代码](https://github.com/smith-gala/2026-source-code)
- [b站课程视频](https://www.bilibili.com/video/BV1TP411v7v6/?spm_id_from=333.337.search-card.all.click)

本项目在园项目的基础上进行了整理，升级与二次开发，目前仍在开发中

## ✨ 项目亮点

开发中，待填写

## 🚀 功能模块

## 🛠 技术栈

| 模块 | 技术 | 端口 |
| --- | --- | --- |
| 后端 | Spring Boot 2.7.3、MyBatis、Druid、Redis、WebSocket、JWT、POI、Knife4j | 8080 |
| 管理端前端 | Vue 2 + TypeScript + Element UI | 8081（dev server） |
| 微信小程序 | uni-app | — |
| 数据库 | MySQL 8，库名 `sky_take_out` | 3306 |
| 缓存 | Redis，`database` 为 **0** | 6379 |


## ⚡ 快速开始

### 环境信息

| 环境/工具 | 版本或说明 |
| --- | --- |
| 操作系统 | 待填写 |
| JDK | 待填写 |
| Maven | 待填写 |
| Node.js | 待填写 |
| npm | 待填写 |
| MySQL | 待填写 |
| Redis | 待填写 |
| IntelliJ IDEA | 待填写 |
| 微信开发者工具（运行小程序时需要） | 待填写 |

### 1. 初始化数据库

```bash
mysql -u用户名 -p < db/sky.sql
```

建 11 张表，并写入初始数据。

### 2. 启动 Redis

后端连接的 `database` 是 **0**，确认本地 Redis 已启动且该库可用。

### 3. 启动后端

先改 `sky-take-out/sky-server/src/main/resources/application-dev.yml`：

| 配置项 | 说明 |
| --- | --- |
| `sky.datasource.host / port / username / password` | 改成你自己的 MySQL |
| `sky.redis.host / port` | 改成你自己的 Redis |
| `sky.upload-path` | 改成你本机用于保存上传图片的目录，并确保该目录可写 |
| `sky.wechat.*` | 微信支付需要真实商户号与证书文件，学习阶段可整段跳过 |

然后运行 `sky-take-out/sky-server` 下的 `com.sky.SkyApplication`，服务端口 **8080**。

### 4. 启动管理端前端（开发模式，推荐）

```bash
cd project-rjwm-admin-vue-ts
npm install
npm run serve
```

访问 http://localhost:8081。

开发环境由 Vue Dev Server 提供前端页面并代理后端请求，**不需要启动 Nginx**。`.env.development` 中的相关配置如下：

```
VUE_APP_BASE_API = '/api'
VUE_APP_URL      = 'http://localhost:8080/admin'   # /api 请求代理到这里
VUE_APP_SOCKET_URL = 'ws://localhost:8080/ws/'     # 来单提醒 WebSocket
```

### 5. 启动微信小程序（可选）

用微信开发者工具打开 `mp-weixin/` 目录。

## 默认账号

| 端 | 账号 | 密码 |
| --- | --- | --- |
| 管理端 | `admin` | `123456` |

## 🏗 系统架构

## 📂 项目结构

```
sky-delivery-plus/
├── sky-take-out/                  # 后端（Maven 多模块）
│   ├── sky-common/                # 常量、工具类、自定义异常、统一返回结果
│   ├── sky-pojo/                  # 项目所需要用到的对象类,entity / DTO / VO
│   └── sky-server/                # 启动类、config、interceptor、员工登录；其余业务待编写
├── project-rjwm-admin-vue-ts/     # 管理端前端（Vue 2 + TypeScript）
├── mp-weixin/                     # 微信小程序（用户端）
└── db/
    ├── sky.sql                    # 建库建表 + 初始数据
    └── 数据库设计文档.md
```

## 🗄 数据库设计

见 `db/数据库设计文档.md`。

## ⚙️ 配置说明

## 📚 接口文档


## 📝 重构与开发记录

## 🗺 后续计划

1. 补充项目的相关测试
2. 升级相关技术栈版本
3. to be continued...


## 🖼 项目截图
