package com.azure.support;

import com.azure.config.WebMvcConfiguration;
import com.azure.handler.GlobalExceptionHandler;
import com.azure.interceptor.*;
import com.azure.json.JacksonObjectMapper;
import com.azure.mapper.EmployeeMapper;
import com.azure.properties.*;
import com.azure.service.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;

/** Load real MVC registration, interceptors, JSON conversion, advice and cache proxies only. */
@org.springframework.boot.test.context.TestConfiguration
@EnableCaching
// 测试用：为未加载完整 Boot 自动配置的独立 MVC 测试提供基础设施。
@EnableWebMvc
@ComponentScan(basePackages="com.azure.controller",useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,classes=RestController.class))
@Import({JacksonObjectMapper.class,WebMvcConfiguration.class,JwtTokenAdminInterceptor.class,JwtTokenUserInterceptor.class,GlobalExceptionHandler.class,com.azure.service.impl.FileUploadServiceImpl.class})
@SuppressWarnings("rawtypes")
public class WebTestConfiguration {
    // 公开测试数据：各自解码为 32 字节，只用于自动化测试。
    public static final String ADMIN_SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    public static final String USER_SECRET = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
    @Bean public JwtProperties jwtProperties() {
        JwtProperties p = new JwtProperties(); p.setAdminSecretKey(ADMIN_SECRET); p.setAdminTtl(60000); p.setAdminTokenName("token");
        p.setUserSecretKey(USER_SECRET); p.setUserTtl(60000); p.setUserTokenName("authentication"); return p;
    }
    @Bean public EmployeeService employeeService() { return mock(EmployeeService.class); }
    @Bean public EmployeeMapper employeeMapper() { return mock(EmployeeMapper.class); }
    @Bean public CategoryService categoryService() { return mock(CategoryService.class); }
    @Bean public DishService dishService() { return mock(DishService.class); }
    @Bean public SetmealService setmealService() { return mock(SetmealService.class); }
    @Bean public AddressBookService addressBookService() { return mock(AddressBookService.class); }
    @Bean public ShoppingCartService shoppingCartService() { return mock(ShoppingCartService.class); }
    @Bean public OrdersService ordersService() { return mock(OrdersService.class); }
    @Bean public UserService userService() { return mock(UserService.class); }
    @Bean public ReportService reportService() { return mock(ReportService.class); }
    @Bean public WorkspaceService workspaceService() { return mock(WorkspaceService.class); }
    @Bean public ShopService shopService() { return mock(ShopService.class); }
    @Bean public CacheManager cacheManager() { return new ConcurrentMapCacheManager("setmealCache","setmealDishCache"); }
}
