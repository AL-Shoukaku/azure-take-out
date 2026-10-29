package com.azure.web;

import com.azure.AzureApplication;
import com.azure.cache.CacheClient;
import com.azure.context.BaseContext;
import com.azure.properties.JwtProperties;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT, services, SQL and cache proxies; external Redis is replaced by an in-memory cache. */
@SpringBootTest(classes=AzureApplication.class,properties={
        "spring.datasource.druid.url=jdbc:h2:mem:azure_setmeal_cache_mvc;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
        "spring.cache.type=simple"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SetmealCacheMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CacheManager cacheManager;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean private RedissonClient redissonClient;
    // 套餐读写已走 CacheClient（真实 Redis），本测试环境没有 Redis：mock 成透传 loader，
    // 让测试聚焦"改套餐后列表与详情正确重载"的服务与数据库行为，清缓存调用由 mock 吸收。
    @MockitoBean private CacheClient cacheClient;
    @MockitoBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;
    @BeforeEach @SuppressWarnings({"rawtypes","unchecked"}) void bypassCache() {
        when(cacheClient.getOrLoad(anyString(), any(), any(), anyLong()))
                .thenAnswer(inv -> ((Supplier)inv.getArgument(2)).get());
    }
    @AfterEach void clearState() {
        BaseContext.removeCurrentId();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getUserTokenName(),JwtUtil.createJWT(jwt.getUserSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("userId",42L))));
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getAdminTokenName(),JwtUtil.createJWT(jwt.getAdminSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("empId",7L))));
    }
    @Test void editingSetmealReloadsDishContentsAndBothOldAndNewCategoryLists() throws Exception {
        jdbc.update("insert into category (id,type,name,sort,status) values (4,2,'新套餐分类',4,1)");
        mvc.perform(user(get("/user/setmeal/list").param("categoryId","3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("单人套餐"));
        mvc.perform(user(get("/user/setmeal/list").param("categoryId","4")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(user(get("/user/setmeal/dish/1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(admin(put("/admin/setmeal").contentType("application/json")
                .content("{\"id\":1,\"name\":\"修改后的套餐\",\"categoryId\":4,\"price\":25.00,\"status\":1,"
                        + "\"setmealDishes\":[{\"dishId\":2,\"name\":\"米饭\",\"price\":8.00,\"copies\":3}]}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(jdbc.queryForObject("select dish_id from setmeal_dish where setmeal_id=1",Long.class)).isEqualTo(2L);
        mvc.perform(user(get("/user/setmeal/dish/1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("米饭"))
                .andExpect(jsonPath("$.data[0].copies").value(3));
        mvc.perform(user(get("/user/setmeal/list").param("categoryId","3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(user(get("/user/setmeal/list").param("categoryId","4")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("修改后的套餐"));
        assertThat(BaseContext.getCurrentId()).isNull();
    }
}
