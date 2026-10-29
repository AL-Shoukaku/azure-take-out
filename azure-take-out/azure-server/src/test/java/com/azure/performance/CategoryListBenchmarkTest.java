package com.azure.performance;

import com.azure.entity.Category;
import com.azure.service.CategoryService;
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
 * 该接口目前没有缓存，每次请求 1 条 SQL；作为后续为分类列表接入 CacheClient 的对照基线，
 * 接入后重跑本测试即可对比性能与打库次数的变化。
 *
 * <p>隔离措施：额外写入 100 个压测分类（ID 980001~980100）模拟大菜单规模，
 * 结束后自动删除；Redis 使用隔离的 15 号库，结束后由基类清空。
 *
 * <p>运行方式（终端需 JAVA_HOME 指向 JDK 21；IDEA 中加环境变量 CACHE_BENCH=true）：
 * <pre>JAVA_HOME=... CACHE_BENCH=true mvn test -pl azure-server -am -Dtest=CategoryListBenchmarkTest</pre>
 */
public class CategoryListBenchmarkTest extends PerformanceBenchmarkSupport {

    /** 追加的压测分类数，模拟真实门店的"大类目"菜单规模 */
    private static final int SEEDED_CATEGORIES = 100;
    private static final long FIRST_SEEDED_ID = 980001L;
    private static final int TYPE_DISH = 1;
    private static final int THREADS = 20;
    private static final int REQUESTS_PER_THREAD = 200;

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

        // 热身：JIT、连接池、MyBatis 语句解析、InnoDB buffer pool
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

        SqlCountInterceptor.drain();
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
        String report = String.format("""
                ==================== 分类列表查询基准结果(categoryService.list) ====================
                场景: %d 个启用菜品分类, %d 线程 × %d 次请求 = %d 次请求, 当前无缓存
                环境: 本地开发库(压测分类 ID≥980000), Redis 15 号库, 服务层直调(不含 HTTP 开销)

                单次请求耗时: mean=%.3fms  p50=%.3fms  p95=%.3fms  max=%.3fms
                全部完成总耗时=%.2fms
                实际打库: %d 条 [CategoryMapper.list×%d]
                平均每请求打库: %.3f 条  (当前无缓存=每请求 1 条; 接入 CacheClient 后应≈1 条)
                ============================================================================""",
                expectedSize, THREADS, REQUESTS_PER_THREAD, totalRequests,
                s[0], s[1], s[2], s[3],
                burstNanos / 1_000_000.0,
                sqlTotal, sql.getOrDefault("com.azure.mapper.CategoryMapper.list", 0L),
                (double) sqlTotal / totalRequests);
        System.out.println(report);
        writeReport("category-list-bench-report.txt", report);
    }
}
