package com.azure.support;

import com.azure.cache.CacheClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Use actual @Transactional metadata and the same datasource as the SQL tests. */
public abstract class TransactionTestSupport extends SqlTestSupport {
    @org.junit.jupiter.api.BeforeEach
    void resetTransactionalFixtures() {
        // These tests need independent committed transactions, so reset instead of an outer test transaction.
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("sql/schema.sql"),
                new org.springframework.core.io.ClassPathResource("sql/data.sql")).execute(dataSource);
    }
    protected <T> T transactionalMapper(Class<T> type) { return new SqlSessionTemplate(factory).getMapper(type); }
    protected static void inject(Object target,String field,Object value) { ReflectionTestUtils.setField(target,field,value); }
    /** 内部依赖全部 mock 的真实 CacheClient，供手动装配的服务实现注入使用（成功提交事务后 afterCommit 清缓存也能安全执行） */
    protected static CacheClient mockedCacheClient() {
        CacheClient client = new CacheClient();
        inject(client,"stringRedisTemplate",mock(StringRedisTemplate.class));
        inject(client,"objectMapper",new ObjectMapper());
        RedissonClient redisson = mock(RedissonClient.class);
        when(redisson.getKeys()).thenReturn(mock(RKeys.class));
        inject(client,"redissonClient",redisson);
        return client;
    }
    @SuppressWarnings("unchecked")
    protected <T> T transactionalService(T target,Class<T> type) {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        return (T)proxy.getProxy();
    }
}
