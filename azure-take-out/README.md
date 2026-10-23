# 碧蓝外卖后端

后端采用 Spring Boot 和 Maven 多模块结构，使用 JDK 21。本地环境配置、启动方式与默认账号见 [项目 README](../README.md)。

## 主要技术栈及版本

Maven、MySQL、Redis 版本为当前本地环境版本。

| 技术 / 组件 | 版本 | 主要用途 |
| --- | --- | --- |
| JDK | 21 | Java 运行与编译环境 |
| Maven | 3.9.12 | 多模块构建、依赖与插件管理 |
| Spring Boot | 3.5.16 | 应用启动与自动配置 |
| MyBatis | 3.5.19 | 数据持久化与 SQL 映射 |
| MySQL | 8.4.11 | 业务数据存储 |
| Redis | 8.0.5 | 缓存与店铺营业状态存储 |
| JJWT | 0.13.0 | JWT 签发与校验 |
| Apache POI | 5.5.1 | Excel 运营数据报表生成与导出 |

## 模块划分

```text
azure-take-out/
├── pom.xml                                    # 父工程：模块聚合、版本与依赖管理
├── azure-common/                              # 常量、上下文、异常、JSON 与通用工具
├── azure-pojo/                                # 请求 DTO、数据库实体与响应 VO
└── azure-server/                              # 应用启动、接口、业务、数据访问与测试
```

演示商品图片位于仓库根目录的 [`assets/initImage`](../assets/initImage/)。Maven 构建时自动将图片复制到 `classpath:/menu-images/`，由后端通过 `/menu-images/**` 提供访问。

## 目录结构

### azure-common

```text
azure-common/
├── pom.xml                                    # 公共模块依赖配置
└── src/main/java/com/azure/
    ├── constant/                              # 状态、JWT 声明、默认密码等常量
    ├── context/                               # 当前登录身份的线程上下文
    ├── enumeration/                           # 公共字段填充的操作类型枚举
    ├── exception/                             # 自定义业务异常
    ├── json/
    │   └── JacksonObjectMapper.java           # Jackson 序列化规则与日期时间格式
    ├── properties/                            # JWT 配置属性
    ├── result/                                # 统一响应结果与分页结果
    └── utils/                                 # JWT 签发与校验、HTTP 请求工具
```

### azure-pojo

```text
azure-pojo/
├── pom.xml                                    # 数据对象模块依赖配置
└── src/main/java/com/azure/
    ├── dto/                                   # 接收请求参数的数据传输对象
    ├── entity/                                # 与数据库表对应的实体类
    └── vo/                                    # 返回给前端的响应对象
```

### azure-server

```text
azure-server/
├── pom.xml                                    # 服务模块依赖与构建配置
└── src/
    ├── main/
    │   ├── java/com/azure/
    │   │   ├── AzureApplication.java          # Spring Boot 启动类
    │   │   ├── annotation/                    # 自定义注解
    │   │   ├── aspect/                        # 公共字段自动填充等 AOP 切面
    │   │   ├── config/                        # MVC、Redis、WebSocket、OpenAPI 配置
    │   │   ├── controller/                    # 接口控制器
    │   │   │   ├── admin/                     # 管理端接口
    │   │   │   └── user/                      # 用户端接口
    │   │   ├── handler/                       # 全局异常处理
    │   │   ├── interceptor/                   # 管理端与用户端 JWT 拦截器
    │   │   ├── mapper/                        # MyBatis 数据访问接口
    │   │   ├── security/
    │   │   │   └── PasswordHasher.java        # Argon2id 密码哈希、验证与旧 MD5 兼容
    │   │   ├── service/                       # 业务服务接口
    │   │   │   └── impl/                      # 业务服务实现
    │   │   ├── task/                          # 订单定时任务
    │   │   └── websocket/                     # 来单提醒与催单消息推送
    │   └── resources/
    │       ├── application.yml                # 主配置，默认启用 local 环境
    │       ├── application-local.example.yml  # 本地配置示例
    │       ├── application-local.yml          # 本机配置（不提交 Git）
    │       ├── mapper/                        # MyBatis SQL 映射文件
    │       └── template/                      # 运营数据报表模板
    └── test/
        ├── java/com/azure/                    # 公共模块、接口、业务、数据访问及集成测试
        └── resources/                        # 测试配置与 SQL 数据
```

## 验证

在本目录执行：

```sh
mvn clean verify
```

测试使用 H2 和项目内的测试配置，覆盖接口、业务、SQL 映射及演示图片访问，不依赖本机业务数据库。覆盖率报告生成在 `azure-server/target/site/jacoco/index.html`。
