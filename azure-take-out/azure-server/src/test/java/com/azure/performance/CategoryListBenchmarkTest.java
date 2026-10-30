package com.azure.performance;

import com.azure.cache.RedisConstants;
import com.azure.service.CategoryService;
import com.azure.entity.Category;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分类列表查询基准：对应用户端 /user/category/list 背后的 categoryService.list(type)。
 * 用于对比接入 CacheClient 前后的性能与打库次数——输出格式与缓存状态无关，
 * 开关缓存后各跑一轮，两份报告可直接逐行对比。
 *
 * <p>隔离措施：额外写入 100 个压测分类（ID 980001~980100）模拟大菜单规模，
 * 结束后自动删除；Redis 使用隔离的 15 号库，结束后由基类清空。
 *
 * <p>运行方式（终端需 JAVA_HOME 指向 JDK 21；IDEA 中加环境变量 CACHE_BENCH=true）：
 * <pre>JAVA_HOME=... CACHE_BENCH=true mvn test -pl azure-server -am -Dtest=CategoryListBenchmarkTest</pre>
 * 单独运行本类不会执行其他测试，因此在临时注释掉缓存做"加缓存前"测量时，其余测试不受影响。
 */
public class CategoryListBenchmarkTest extends PerformanceBenchmarkSupport {

    /** 追加的压测分类数，模拟真实门店的"大类目"菜单规模 */
    private static final int SEEDED_CATEGORIES = 100;
    private static final long FIRST_SEEDED_ID = 980001L;
    private static final int TYPE_DISH = 1;
    private static final int THREADS = 20;
    private static final int REQUESTS_PER_THREAD = 200;
    private static final int COLD_START_ROUNDS = 5;
    /** 直接引用服务的 key 前缀常量，key 格式变化时自动跟随 */
    private static final String BENCH_KEY = RedisConstants.CATEGORY_KEY_PREFIX + TYPE_DISH;

    @Autowired private CategoryService categoryService;
    private int expectedSize;

    @BeforeAll
    void seedCategories() {
        cleanBenchRows();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= SEEDED_CATEGORIES; i++) {
            rows.add(new Object[]{FIRST_SEEDED_ID + i - 1, TYPE_DISH, "压测分类" + i, i, 1});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO category (id, type, name, sort, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, now(), now())""", rows);
        expectedSize = jdbcTemplate.queryForObject(
                "select count(*) from category where status = 1 and type = ?", Integer.class, TYPE_DISH);

        // 热身：JIT、连接池、MyBatis 语句解析、InnoDB buffer pool；分类缓存若已启用，此阶段同时填充缓存
        for (int i = 0; i < 30; i++) {
            categoryService.list(TYPE_DISH);
        }
    }

    @AfterAll
    void cleanUp() {
        cleanBenchRows();
    }

    /** 只删除本测试写入的压测分类 */
    private void cleanBenchRows() {
        jdbcTemplate.update("DELETE FROM category WHERE id BETWEEN ? AND ?",
                FIRST_SEEDED_ID, FIRST_SEEDED_ID + SEEDED_CATEGORIES - 1);
    }

    @Test
    void benchmarkCategoryListQuery() throws Exception {
        int totalRequests = THREADS * REQUESTS_PER_THREAD;

        SqlCountInterceptor.drain(); // 丢弃热身期间的计数，只统计测量阶段
        // 冷启动：每轮先删 key 再请求，单独测量"缓存未命中重建"的那条 SQL。
        // 有缓存时每轮 1 条(重建)，无缓存时同样每轮 1 条(本就在直查)，两种状态数字可比。
        long[] coldNanos = new long[COLD_START_ROUNDS];
        for (int i = 0; i < COLD_START_ROUNDS; i++) {
            redisTemplate.delete(BENCH_KEY);
            long t0 = System.nanoTime();
            assertThat(categoryService.list(TYPE_DISH)).hasSize(expectedSize);
            coldNanos[i] = System.nanoTime() - t0;
        }
        long coldSql = totalSql(SqlCountInterceptor.drain());
        assertThat(coldSql).as("冷启动每轮应恰好 1 条 SQL").isEqualTo(COLD_START_ROUNDS);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        long burstNanos;
        long[] nanos;
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    List<Long> samples = new ArrayList<>(REQUESTS_PER_THREAD);
                    start.await();
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        long t0 = System.nanoTime();
                        List<Category> list = categoryService.list(TYPE_DISH);
                        samples.add(System.nanoTime() - t0);
                        assertThat(list).as("分类列表条数应稳定").hasSize(expectedSize);
                    }
                    return samples;
                }));
            }
            long t0 = System.nanoTime();
            start.countDown();
            List<Long> all = new ArrayList<>(totalRequests);
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get()); // 线程内断言失败会在这里抛出
            }
            burstNanos = System.nanoTime() - t0;
            nanos = all.stream().mapToLong(Long::longValue).toArray();
        } finally {
            pool.shutdownNow();
        }
        Map<String, Long> sql = SqlCountInterceptor.drain();
        long sqlTotal = totalSql(sql);

        double[] s = stats(nanos);
        double[] cold = stats(coldNanos);
        String report = String.format("""
                ==================== 分类列表查询基准结果(categoryService.list) ====================
                场景: %d 个启用菜品分类, %d 线程 × %d 次请求 = %d 次请求
                环境: 本地开发库(压测分类 ID≥980000), Redis 15 号库, 服务层直调(不含 HTTP 开销)

                单次请求耗时: mean=%.3fms  p50=%.3fms  p95=%.3fms  max=%.3fms
                全部完成总耗时=%.2fms
                实际打库: %d 条 [%s]
                平均每请求打库: %.3f 条

                冷启动(每轮先删 key 再请求, %d 轮): mean=%.3fms  p50=%.3fms  打库 %d 条 (每轮 1 条)
                ============================================================================""",
                expectedSize, THREADS, REQUESTS_PER_THREAD, totalRequests,
                s[0], s[1], s[2], s[3],
                burstNanos / 1_000_000.0,
                sqlTotal, describeSql(sql),
                (double) sqlTotal / totalRequests,
                COLD_START_ROUNDS, cold[0], cold[1], coldSql);
        System.out.println(report);
        writeReport("category-list-bench-report.txt", report);
    }
}
