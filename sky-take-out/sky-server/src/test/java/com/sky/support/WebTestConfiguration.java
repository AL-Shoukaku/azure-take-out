package com.sky.support;

import com.sky.config.WebMvcConfiguration;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.interceptor.*;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.*;
import com.sky.service.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.*;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.swagger.readers.operation.SwaggerOperationModelsProvider;
import static org.mockito.Mockito.*;

/** Load real MVC registration, interceptors, JSON conversion, advice and cache proxies only. */
@org.springframework.boot.test.context.TestConfiguration
@EnableCaching
@ComponentScan(basePackages="com.sky.controller",useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,classes=RestController.class))
@Import({WebMvcConfiguration.class,JwtTokenAdminInterceptor.class,JwtTokenUserInterceptor.class,GlobalExceptionHandler.class})
@SuppressWarnings("rawtypes")
public class WebTestConfiguration {
    public static final String ADMIN_SECRET = "admin-secret-for-isolated-tests-32";
    public static final String USER_SECRET = "user-secret-for-isolated-tests-32";
    @Bean public JwtProperties jwtProperties() {
        JwtProperties p = new JwtProperties(); p.setAdminSecretKey(ADMIN_SECRET); p.setAdminTtl(60000); p.setAdminTokenName("token");
        p.setUserSecretKey(USER_SECRET); p.setUserTtl(60000); p.setUserTokenName("authentication"); return p;
    }
    @Bean public WeChatProperties weChatProperties() {
        WeChatProperties p = new WeChatProperties(); p.setApiV3Key("0123456789abcdef0123456789abcdef"); return p;
    }
    @Bean public SwaggerOperationModelsProvider swaggerOperationModelsProvider() { return mock(SwaggerOperationModelsProvider.class); }
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
    @Bean public RedisTemplate redisTemplate() { return mock(RedisTemplate.class); }
    @Bean public ValueOperations valueOperations() { return mock(ValueOperations.class); }
    @Bean public CacheManager cacheManager() { return new ConcurrentMapCacheManager("setmealCache","setmealDishCache"); }
}
