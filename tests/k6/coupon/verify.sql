-- 在测试结束后立即执行；先替换为控制台/报告里的券 ID 和初始库存。
-- 只读查询，不修改数据。购券测试只建待支付订单，应在15分钟超时释放前核对。
SET @coupon_id = 123;
SET @initial_stock = 10;

-- 1. 数据库库存与实际占用是否一致：正常应 stock_matches = 1，stock >= 0。
-- 免费券按已发放张数计；购券按待支付/已支付订单计，已关闭/退款订单不再占库存。
SELECT checked.*,
       (@initial_stock - allocated_count = stock) AS stock_matches,
       (allocated_count > @initial_stock) AS oversold
FROM (
    SELECT c.id, c.name, c.type, c.stock,
           CASE WHEN c.type = 1 THEN
               (SELECT COUNT(*) FROM coupon_order o WHERE o.coupon_id = c.id AND o.status IN (0, 1))
           ELSE (SELECT COUNT(*) FROM user_coupon u WHERE u.coupon_id = c.id) END AS allocated_count
    FROM coupon c WHERE c.id = @coupon_id
) AS checked;

-- 2. 同用户同券重复订单：正常应返回0行。不要只看 user_coupon 的唯一约束。
SELECT user_id, COUNT(*) AS order_count
FROM coupon_order WHERE coupon_id = @coupon_id
GROUP BY user_id HAVING COUNT(*) > 1;

-- 3. 同用户重复发券：正常应返回0行。
SELECT user_id, COUNT(*) AS coupon_count
FROM user_coupon WHERE coupon_id = @coupon_id
GROUP BY user_id HAVING COUNT(*) > 1;

-- 4. 实际持有人/下单用户数，便于与 k6 的成功响应数核对。
SELECT 'coupon_order' AS source, COUNT(*) AS total_rows, COUNT(DISTINCT user_id) AS distinct_users
FROM coupon_order WHERE coupon_id = @coupon_id
UNION ALL
SELECT 'user_coupon', COUNT(*), COUNT(DISTINCT user_id)
FROM user_coupon WHERE coupon_id = @coupon_id;
