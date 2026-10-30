package com.azure.performance;

import com.azure.AzureApplication;
import com.azure.task.OrderTask;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 性能基准测试公共基类。走 local profile 连接真实本地 MySQL/Redis；Redis 固定使用隔离的
 * 15 号库，测试类结束后自动清空。三个基准类共享同一个 Spring 上下文，只启动一次。
 *
 * <p>所有基准测试用环境变量 CACHE_BENCH=true 做开关，不设置时自动跳过，普通 mvn test 不受影响。
 * 终端运行需要 JAVA_HOME 指向 JDK 21；在 IDEA 中运行只需在运行配置里加 CACHE_BENCH=true。
 */
@EnabledIfEnvironmentVariable(named = "CACHE_BENCH", matches = "true",
        disabledReason = "基准测试需要真实本地 MySQL/Redis：设置 CACHE_BENCH=true 后运行")
@SpringBootTest(classes = AzureApplication.class, properties = {
        // 基准测量期间关闭 MyBatis DEBUG 日志，避免日志 I/O 混入耗时（生产环境不会开 DEBUG）
        "logging.level.com.azure.mapper=info"})
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PerformanceBenchmarkSupport {

    @MockitoBean protected OrderTask orderTask;
    @MockitoBean(name = "serverEndpointExporter") protected ServerEndpointExporter serverEndpointExporter;

    // 与 CacheClient 相同的 StringRedisTemplate：保证删 key 与缓存写入的字符串 key 序列化一致
    @Autowired protected StringRedisTemplate redisTemplate;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected SqlSessionFactory sqlSessionFactory;

    @DynamicPropertySource
    static void isolateBenchEnvironment(DynamicPropertyRegistry registry) {
        registry.add("azure.redis.database", () -> "15");
    }

    @BeforeAll
    void registerSqlCountInterceptor() {
        // 三个基准类共享同一个 Spring 上下文，@BeforeAll 会对每个类各执行一次，
        // 必须保证拦截器只注册一次，否则 SQL 计数会按类序号成倍重复
        SqlCountInterceptor.tryRegister(sqlSessionFactory.getConfiguration());
    }

    @AfterAll
    void flushBenchRedis() {
        // 清空 Redis 15 号库（连接工厂已绑定该库），不影响本地开发缓存
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    /** 把 Mapper 方法计数格式化为 "XxxMapper.yyy×次数, ..."，按次数降序 */
    protected static String describeSql(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey().replace("com.azure.mapper.", "") + "×" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    protected static long totalSql(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 返回 {mean, p50, p95, max}，单位毫秒 */
    protected static double[] stats(long[] nanos) {
        double[] ms = new double[nanos.length];
        for (int i = 0; i < nanos.length; i++) {
            ms[i] = nanos[i] / 1_000_000.0;
        }
        Arrays.sort(ms);
        double mean = Arrays.stream(ms).average().orElse(0);
        return new double[]{mean, ms[(ms.length - 1) / 2], ms[(int) Math.ceil(ms.length * 0.95) - 1], ms[ms.length - 1]};
    }

    /** 把报告同时写到 target 目录，保证结果可留档 */
    protected void writeReport(String fileName, String content) {
        try {
            Files.write(Path.of("target", fileName), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 统计 MyBatis 实际执行的 SQL 条数，按 MappedStatement 分别计数。
     * 只拦截 Executor.query 的 4 参入口重载——它是 SqlSession 进入 Executor 的唯一入口，
     * 每次逻辑查询恰好经过代理一次，不会因 Executor 内部自调用而漏计或重复计数。
     */
    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static class SqlCountInterceptor implements Interceptor {

        private static final ConcurrentHashMap<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
        private static volatile boolean registered = false;

        /** 幂等注册：同一个 MyBatis 配置只挂一次，避免共享上下文时重复计数 */
        static synchronized void tryRegister(org.apache.ibatis.session.Configuration configuration) {
            if (!registered) {
                configuration.addInterceptor(new SqlCountInterceptor());
                registered = true;
            }
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            COUNTS.computeIfAbsent(ms.getId(), k -> new LongAdder()).increment();
            return invocation.proceed();
        }

        /** 取走当前所有计数并清零：key = Mapper 方法全名，value = 执行次数；零计数不保留 */
        static Map<String, Long> drain() {
            Map<String, Long> snapshot = new HashMap<>();
            COUNTS.forEach((k, v) -> {
                long n = v.sumThenReset();
                if (n > 0) {
                    snapshot.put(k, n);
                }
            });
            COUNTS.clear();
            return snapshot;
        }
    }
}
