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

先把 `sky-take-out/sky-server/src/main/resources/application-local.example.yml` 复制为同目录的 `application-local.yml`。后端默认启用 `local` 配置；`application-local.yml` 已加入 `.gitignore`，只在本机保存，不要提交。

在 `application-local.yml` 中填写：

| 配置项 | 说明 |
| --- | --- |
| `sky.datasource.host / port / database / username / password` | 本机 MySQL 地址、端口、库名（默认 `sky_take_out`）、用户名和密码 |
| `sky.redis.host / port / database` | Redis 地址、端口和库编号（默认 `0`） |
| `sky.upload-path` | 上传图片的保存目录，确保后端进程可写；相对路径以启动后端时的工作目录为准 |
| `sky.wechat.appid / secret` | 运行小程序用户登录时，填写微信公众平台中的小程序 AppID 和 AppSecret；只运行管理端可留空 |

这里的微信配置用于**小程序登录**。项目当前支付流程使用模拟支付成功，不需要微信支付商户号或证书。小程序中的 AppID 与后端填写的 AppID 应为同一个。

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

用微信开发者工具打开 `mp-weixin/` 目录。项目内的 `project.config.json` 将 AppID 留空；在本地创建 `mp-weixin/project.private.config.json`，填写自己的 `appid`，例如：

```json
{
  "appid": "你的小程序 AppID"
}
```

`project.private.config.json` 已加入 `.gitignore`。微信开发者工具也可能自动生成它；确认其中的 AppID 与后端 `application-local.yml` 一致。若需要使用微信用户登录，还要填写后端的 `sky.wechat.secret`。

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
