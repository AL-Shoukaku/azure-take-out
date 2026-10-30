package com.azure.support;

import com.azure.context.BaseContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.service.*;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(WebTestConfiguration.class)
@WebAppConfiguration
@SuppressWarnings({"rawtypes","unchecked"})
public abstract class WebTestSupport {
    @Autowired protected WebApplicationContext context;
    @Autowired protected EmployeeService employeeService;
    @Autowired protected CategoryService categoryService;
    @Autowired protected DishService dishService;
    @Autowired protected SetmealService setmealService;
    @Autowired protected AddressBookService addressBookService;
    @Autowired protected ShoppingCartService shoppingCartService;
    @Autowired protected OrdersService ordersService;
    @Autowired protected UserService userService;
    @Autowired protected ReportService reportService;
    @Autowired protected WorkspaceService workspaceService;
    @Autowired protected ShopService shopService;
    @Autowired protected CacheManager cacheManager;
    protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    protected static final Path UPLOADS = createUploadDirectory();
    private static Path createUploadDirectory() {
        try { return Files.createTempDirectory("azure-mvc-upload-"); } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void uploadProperties(DynamicPropertyRegistry registry) { registry.add("azure.upload-path",UPLOADS::toString); }
    @BeforeEach void initializeMvc() {
        reset(employeeService,categoryService,dishService,setmealService,addressBookService,shoppingCartService,
                ordersService,userService,reportService,workspaceService,shopService);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        BaseContext.removeCurrentId(); mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void clearMvcState() throws IOException {
        BaseContext.removeCurrentId();
        ImageFixtures.clearDirectory(UPLOADS);
    }
    protected MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("token",JwtUtil.createJWT(WebTestConfiguration.ADMIN_SECRET,60000,new HashMap<>(Collections.singletonMap("empId",7L))));
    }
    protected MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header("authentication",JwtUtil.createJWT(WebTestConfiguration.USER_SECRET,60000,new HashMap<>(Collections.singletonMap("userId",42L))));
    }
    protected ResultActions successful(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }
}
