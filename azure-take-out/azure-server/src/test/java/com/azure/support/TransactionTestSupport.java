package com.azure.support;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

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
    @SuppressWarnings("unchecked")
    protected <T> T transactionalService(T target,Class<T> type) {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        return (T)proxy.getProxy();
    }
}
