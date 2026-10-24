package com.azure.support;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.azure.context.BaseContext;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

/** Real production MyBatis mappings, isolated H2 database, rollback after every test. */
public abstract class SqlTestSupport {
    protected static DataSource dataSource;
    protected static SqlSessionFactory factory;
    protected SqlSession session;
    protected static final long USER_ID = 42L;

    @BeforeAll static void initializeDatabase(TestInfo info) throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:azure_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("sql/schema.sql"),new ClassPathResource("sql/data.sql")).execute(dataSource);
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(dataSource);
        bean.setTypeAliasesPackage("com.azure.entity");
        bean.setTransactionFactory(TransactionTestSupport.class.isAssignableFrom(info.getTestClass().get())
                ? new org.mybatis.spring.transaction.SpringManagedTransactionFactory()
                : new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(true);
        config.addMappers("com.azure.mapper"); bean.setConfiguration(config);
        PageInterceptor pagination = new PageInterceptor(); Properties properties = new Properties(); properties.setProperty("helperDialect","h2");
        pagination.setProperties(properties); bean.setPlugins(pagination); factory = bean.getObject();
    }
    @BeforeEach void openSession() { session = factory.openSession(false); BaseContext.setCurrentId(USER_ID); }
    @AfterEach void rollbackSession() {
        try { if (session != null) { session.rollback(); session.close(); } }
        finally { PageHelper.clearPage(); BaseContext.removeCurrentId(); }
    }
    @AfterAll static void shutdownDatabase() throws Exception {
        if (dataSource != null) try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
    }
    protected <T> T mapper(Class<T> type) { return session.getMapper(type); }
}
