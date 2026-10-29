package com.azure.performance;

import com.azure.cache.RedisConstants;
import com.azure.service.DishService;
import com.azure.vo.DishVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手动基准测试：测量 DishServiceImpl#listWithFlavorByCategoryId 的缓存在未命中时单次重建的代价。
 * 当前实现为 CacheClient（互斥锁 + Jackson 序列化 + TTL 抖动），本测试保留与原手写
 * RedisTemplate 缓存的对比能力（原版基线数据：重建 p50≈45ms、命中 p50≈4ms、无锁并发
 * 20 线程打库 1020 条），改造后重跑即可对比。
 *
 * <p>隔离措施：基准数据只写入 ID ≥ 900000 的独立行（分类 9001、菜品 900001~900050 及其
 * 口味），测试结束后自动删除；Redis 使用隔离的 15 号库，结束后由基类清空。
 *
 * <p>运行方式（在 azure-take-out/azure-server 目录下，终端需 JAVA_HOME 指向 JDK 21）：
 * <pre>JAVA_HOME=... CACHE_BENCH=true mvn test -pl azure-server -am -Dtest=DishCacheRebuildBenchmarkTest</pre>
 * IDEA 中运行只需在运行配置里加环境变量 CACHE_BENCH=true。菜品数可用 BENCH_DISHES 覆盖。
 */
public class DishCacheRebuildBenchmarkTest extends PerformanceBenchmarkSupport {

    private static final long BENCH_CATEGORY_ID = 9001L;
    private static final long FIRST_DISH_ID = 900001L;
    /** 直接引用服务的 key 前缀常量，服务端改 key 格式时自动跟随 */
    private static final String BENCH_KEY = RedisConstants.DISH_KEY_PREFIX + BENCH_CATEGORY_ID;
    /** 分类内菜品数，可用环境变量覆盖：BENCH_DISHES=7 测真实演示数据的规模 */
    private static final int DISH_COUNT =
            Integer.parseInt(System.getenv().getOrDefault("BENCH_DISHES", "50"));
    private static final int ROUNDS = 30;
    private static final int THREADS = 20;
    private static final int WARMUP_ROUNDS = 3;

    @Autowired private DishService dishService;

    @BeforeAll
    void seedBenchData() {
        // 先清掉上次异常中断可能留下的残留行，保证幂等
        cleanBenchRows();
        jdbcTemplate.update("""
                INSERT INTO category (id, type, name, sort, status, create_time, update_time)
                VALUES (?, 1, '压测分类', 0, 1, now(), now())""", BENCH_CATEGORY_ID);

        List<Object[]> dishRows = new ArrayList<>();
        for (int i = 1; i <= DISH_COUNT; i++) {
            dishRows.add(new Object[]{FIRST_DISH_ID + i - 1, "压测菜品" + i, BENCH_CATEGORY_ID,
                    new BigDecimal("10.00"), "bench.png", "基准压测数据", 1});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO dish (id, name, category_id, price, image, description, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())""", dishRows);

        List<Object[]> flavorRows = new ArrayList<>();
        for (int i = 1; i <= DISH_COUNT; i++) {
            flavorRows.add(new Object[]{FIRST_DISH_ID + i - 1, "辣度",
                    "[{\"name\":\"辣度\",\"value\":[\"不辣\",\"微辣\",\"中辣\"]}]"});
        }
        jdbcTemplate.batchUpdate("INSERT INTO dish_flavor (dish_id, name, value) VALUES (?, ?, ?)", flavorRows);

        // 热身：JIT、连接池、MyBatis 语句解析、InnoDB buffer pool，热身后才开始计时
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            redisTemplate.delete(BENCH_KEY);
            assertThat(dishService.listWithFlavorByCategoryId(BENCH_CATEGORY_ID)).hasSize(DISH_COUNT);
        }
    }

    @AfterAll
    void cleanBenchRows() {
        // 只删除本测试写入的行：分类 9001、菜品 900001~900050 及其口味
        jdbcTemplate.update("DELETE FROM dish_flavor WHERE dish_id BETWEEN ? AND ?",
                FIRST_DISH_ID, FIRST_DISH_ID + DISH_COUNT - 1);
        jdbcTemplate.update("DELETE FROM dish WHERE id BETWEEN ? AND ?",
                FIRST_DISH_ID, FIRST_DISH_ID + DISH_COUNT - 1);
        jdbcTemplate.update("DELETE FROM category WHERE id = ?", BENCH_CATEGORY_ID);
    }

    @Test
    void benchmarkRebuildCost() throws Exception {
        SqlCountInterceptor.drain(); // 丢弃热身期间的计数
        double[] rebuild = measureRebuild();
        Map<String, Long> rebuildSql = SqlCountInterceptor.drain();
        long rebuildSqlPerRound = totalSql(rebuildSql) / ROUNDS;

        double[] hit = measureHit();
        Map<String, Long> hitSql = SqlCountInterceptor.drain();

        double[] concurrent = measureConcurrentColdStart();
        Map<String, Long> concurrentSql = SqlCountInterceptor.drain();

        // 结构校验：单次重建恰好 1+N 条 SQL(N+1 查询)；命中完全不碰数据库。
        // 两个断言在换成 CacheClient 后依然成立(loader 还是同样的查询, 命中走 Redis 不走库)。
        assertThat(rebuildSqlPerRound).as("单次重建的 SQL 条数").isEqualTo(1 + DISH_COUNT);
        assertThat(hitSql).as("命中期间的 SQL 计数").isEmpty();

        double netCost = rebuild[1] - hit[1]; // p50 之差 ≈ 重建净成本
        String report = String.format("""
                ==================== dish 缓存重建基准结果 ====================
                数据规模: %d 个菜品/分类(每个 1 条口味), 重建/命中各 %d 轮, 并发 %d 线程
                环境: 本地开发库(基准行 ID≥900000), Redis 15 号库, 服务层直调(不含 HTTP 开销)

                重建(每次先删 key):   mean=%.2fms  p50=%.2fms  p95=%.2fms  max=%.2fms
                  实际打库: %d 条/次 ×%d 轮 [%s]
                命中(不删 key):       mean=%.2fms  p50=%.2fms  p95=%.2fms  max=%.2fms
                  实际打库: %d 条
                重建净成本(重建p50-命中p50): %.2fms   (N+1 查询: 1 条分类菜品 + %d 条口味)

                并发冷启动(%d 线程同时未命中):
                  最快=%.2fms  p50=%.2fms  最慢=%.2fms  全部完成总耗时=%.2fms
                  实际打库: %d 条 [%s]
                ==================================================================""",
                DISH_COUNT, ROUNDS, THREADS,
                rebuild[0], rebuild[1], rebuild[2], rebuild[3],
                rebuildSqlPerRound, ROUNDS, describeSql(rebuildSql),
                hit[0], hit[1], hit[2], hit[3],
                totalSql(hitSql),
                netCost, DISH_COUNT,
                THREADS, concurrent[0], concurrent[1], concurrent[2], concurrent[3],
                totalSql(concurrentSql), describeSql(concurrentSql));
        System.out.println(report);
        writeReport("dish-cache-rebuild-bench-report.txt", report);
    }

    /** 未命中重建：每轮先删 key，再计时调用服务（包含 DB 的 N+1 查询 + 序列化 + SET） */
    private double[] measureRebuild() {
        long[] nanos = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            redisTemplate.delete(BENCH_KEY);
            long t0 = System.nanoTime();
            List<DishVO> list = dishService.listWithFlavorByCategoryId(BENCH_CATEGORY_ID);
            nanos[i] = System.nanoTime() - t0;
            assertThat(list).hasSize(DISH_COUNT);
        }
        return stats(nanos);
    }

    /** 缓存命中：key 存在，每次只发生 GET + 反序列化 */
    private double[] measureHit() {
        long[] nanos = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            long t0 = System.nanoTime();
            List<DishVO> list = dishService.listWithFlavorByCategoryId(BENCH_CATEGORY_ID);
            nanos[i] = System.nanoTime() - t0;
            assertThat(list).hasSize(DISH_COUNT);
        }
        return stats(nanos);
    }

    /** 并发冷启动：删 key 后 N 个线程同时发起 */
    private double[] measureConcurrentColdStart() throws Exception {
        redisTemplate.delete(BENCH_KEY);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    long t0 = System.nanoTime();
                    List<DishVO> list = dishService.listWithFlavorByCategoryId(BENCH_CATEGORY_ID);
                    long elapsed = System.nanoTime() - t0;
                    assertThat(list).hasSize(DISH_COUNT);
                    return elapsed;
                }));
            }
            long burstStart = System.nanoTime();
            start.countDown();
            long[] nanos = new long[THREADS];
            for (int i = 0; i < THREADS; i++) {
                nanos[i] = futures.get(i).get(); // 线程内断言失败会在这里抛出
            }
            long totalNanos = System.nanoTime() - burstStart;
            double[] result = stats(nanos);
            return new double[]{result[0], result[1], result[2], totalNanos / 1_000_000.0};
        } finally {
            pool.shutdownNow();
        }
    }
}
