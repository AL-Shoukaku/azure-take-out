package com.sky.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI配置类，用于生成 API 文档
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI skyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("苍穹外卖项目接口文档")
                .version("0.4.0")
                .description("管理端与用户端接口"));
    }

    @Bean
    public GroupedOpenApi adminOpenApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("管理端接口文档")
                .packagesToScan("com.sky.controller.admin")
                .pathsToMatch("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userOpenApi() {
        return GroupedOpenApi.builder()
                .group("user")
                .displayName("用户端接口文档")
                .packagesToScan("com.sky.controller.user")
                .pathsToMatch("/user/**")
                .build();
    }
}
