package com.azure.performance;

import com.azure.service.DishService;
import com.azure.vo.DishVO;
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
 * 缓存穿透基准：大量请求"不存在的分类 id"。
 *
 * <p>原版实现的问题：查询结果为空时虽然会把空列表写进缓存，但命中判断要求"非空列表"
 * （dishVOList != null && !dishVOList.isEmpty()），空值永远算未命中——因此对不存在 id 的
 * 每个请求都会打一次库。CacheClient 的空值哨兵（缓存空串 + 30s TTL）预期把打库次数从
 * "每请求 1 条"降到"每个 id 仅首次 1 条"，这是本测试要对比的核心指标。
 *
 * <p>隔离措施：请求的分类 id（990001~990020）在库中本就不存在，测试不写任何数据行；
 * Redis 使用隔离的 15 号库，结束后由基类清空。
 *
 * <p>运行方式（终端需 JAVA_HOME 指向 JDK 21；IDEA 中加环境变量 CACHE_BENCH=true）：
 * <pre>JAVA_HOME=... CACHE_BENCH=true mvn test -pl azure-server -am -Dtest=CachePenetrationBenchmarkTest</pre>
 */
public class CachePenetrationBenchmarkTest extends PerformanceBenchmarkSupport {

    /** 不存在的分类 id 个数（改造后每个 id 只应在首次请求打 1 条 SQL） */
    private static final int NONEXISTENT_IDS = 20;
    private static final long FIRST_FAKE_ID = 990001L;
    private static final int THREADS = 20;
    private static final int REQUESTS_PER_THREAD = 100;
    /** 热身用的另一个不存在 id，不参与统计，避免污染测量 */
    private static final long WARMUP_ID = 990999L;

    @Autowired private DishService dishService;

    @BeforeAll
    void warmUp() {
        // 确认热身 id 确实不存在，顺带让 JIT、连接池、MyBatis 语句解析就绪
        assertThat(dishService.listWithFlavorByCategoryId(WARMUP_ID)).isEmpty();
        for (int i = 0; i < 30; i++) {
            dishService.listWithFlavorByCategoryId(WARMUP_ID);
        }
    }

    @Test
    void benchmarkPenetration() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < NONEXISTENT_IDS; i++) {
            ids.add(FIRST_FAKE_ID + i);
        }
        int totalRequests = THREADS * REQUESTS_PER_THREAD;

        SqlCountInterceptor.drain();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        long burstNanos;
        long[] nanos;
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadIdx = t;
                futures.add(pool.submit(() -> {
                    List<Long> samples = new ArrayList<>(REQUESTS_PER_THREAD);
                    start.await();
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        long id = ids.get((threadIdx + i) % ids.size());
                        long t0 = System.nanoTime();
                        List<DishVO> list = dishService.listWithFlavorByCategoryId(id);
                        samples.add(System.nanoTime() - t0);
                        assertThat(list).as("不存在的分类应返回空列表, id=%d", id).isEmpty();
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
                ==================== 缓存穿透基准结果(不存在的分类 id) ====================
                场景: %d 个不存在的分类 id, %d 线程 × %d 次请求 = %d 次请求
                环境: 本地开发库, Redis 15 号库, 服务层直调(不含 HTTP 开销)

                单次请求耗时: mean=%.3fms  p50=%.3fms  p95=%.3fms  max=%.3fms
                全部完成总耗时=%.2fms
                实际打库: %d 条 [%s]
                平均每请求打库: %.3f 条  (无防护基线=每请求 1 条; 空值哨兵防护后应≈%d 条)
                =========================================================================""",
                NONEXISTENT_IDS, THREADS, REQUESTS_PER_THREAD, totalRequests,
                s[0], s[1], s[2], s[3],
                burstNanos / 1_000_000.0,
                sqlTotal, describeSql(sql),
                (double) sqlTotal / totalRequests, NONEXISTENT_IDS);
        System.out.println(report);
        writeReport("cache-penetration-bench-report.txt", report);
    }
}
