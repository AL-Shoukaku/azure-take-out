package com.azure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.interceptor.JwtTokenAdminInterceptor;
import com.azure.interceptor.JwtTokenUserInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${azure.upload-path}")
    private String uploadPath;

    /**
     * 注册 jwt 拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login",
                                    "/user/shop/status",
                                    "/user/user/register");
    }

    /**
     * 设置静态资源映射，将上传的图片存储到本地的 uploadPath 目录下，并通过 /images/** 映射访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/menu-images/**")
                .addResourceLocations("classpath:/menu-images/");

        Path directory = Paths.get(uploadPath).toAbsolutePath().normalize();
        String location = directory.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/images/admin/**")
                .addResourceLocations(location + "admin/");
        registry.addResourceHandler("/images/user/**")
                .addResourceLocations(location + "user/");
        // 旧管理端图片移动到 admin 后，原 /images/文件名 地址仍然可用。
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location + "admin/");
    }

    /**
     * 扩展 SpringMVC 的消息转换器
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                jsonConverter.setObjectMapper(objectMapper);
            }
        }
    }
}
