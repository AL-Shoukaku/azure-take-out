-- 在测试数据库中执行。先停止后端和压测，避免定时任务或新请求同时修改数据。
-- 默认仅预览；确认目标数量和检查结果后，将 @apply 改为1，再完整执行一次。
-- 不重建数据库、不重置自增ID，不删除普通账号或普通优惠券。
SET @apply = 0;

-- NULL表示清理本脚本所有批次；也可填写账号/券名里的14位批次号，例如 '11091935494b6b'。
SET @run_id = NULL;

DROP TEMPORARY TABLE IF EXISTS k6_cleanup_users;
DROP TEMPORARY TABLE IF EXISTS k6_cleanup_coupons;
START TRANSACTION;

-- 严格匹配 prepare.py 的完整命名格式，不能直接用 LIKE 'k6_%'（下划线是通配符）。
CREATE TEMPORARY TABLE k6_cleanup_users AS
SELECT id, account FROM `user`
WHERE account REGEXP '^k6c_[0-9]{10}[0-9a-f]{4}_[0-9]{5,}$'
  AND (@run_id IS NULL OR SUBSTRING(account, 5, 14) = @run_id);

CREATE TEMPORARY TABLE k6_cleanup_coupons AS
SELECT id, name FROM coupon
WHERE name REGEXP '^k6_[0-9]{10}[0-9a-f]{4}_[pos]_[cb]$'
  AND (@run_id IS NULL OR SUBSTRING(name, 4, 14) = @run_id);

-- 出现脚本之外的业务活动时整批拒绝删除，不自动删除那些关联数据。
SET @foreign_holders = (
    SELECT COUNT(*) FROM user_coupon u
    JOIN k6_cleanup_coupons c ON c.id = u.coupon_id
    LEFT JOIN k6_cleanup_users t ON t.id = u.user_id WHERE t.id IS NULL
);
SET @foreign_buyers = (
    SELECT COUNT(*) FROM coupon_order o
    JOIN k6_cleanup_coupons c ON c.id = o.coupon_id
    LEFT JOIN k6_cleanup_users t ON t.id = o.user_id WHERE t.id IS NULL
);
SET @other_user_coupons = (
    SELECT COUNT(*) FROM user_coupon u
    JOIN k6_cleanup_users t ON t.id = u.user_id
    LEFT JOIN k6_cleanup_coupons c ON c.id = u.coupon_id WHERE c.id IS NULL
);
SET @other_coupon_orders = (
    SELECT COUNT(*) FROM coupon_order o
    JOIN k6_cleanup_users t ON t.id = o.user_id
    LEFT JOIN k6_cleanup_coupons c ON c.id = o.coupon_id WHERE c.id IS NULL
);
SET @paid_orders = (
    SELECT COUNT(*) FROM coupon_order o
    JOIN k6_cleanup_coupons c ON c.id = o.coupon_id WHERE o.status NOT IN (0, 3)
);
SET @used_coupons = (
    SELECT COUNT(*) FROM user_coupon u
    JOIN k6_cleanup_coupons c ON c.id = u.coupon_id
    WHERE u.status NOT IN (0, 3) OR u.lock_order_id IS NOT NULL
);
SET @use_records = (
    SELECT COUNT(*) FROM coupon_use_record r JOIN k6_cleanup_coupons c ON c.id = r.coupon_id
);
SET @user_use_records = (
    SELECT COUNT(*) FROM coupon_use_record r JOIN k6_cleanup_users t ON t.id = r.user_id
);
SET @food_orders = (
    SELECT COUNT(*) FROM orders o JOIN k6_cleanup_users t ON t.id = o.user_id
);
SET @addresses = (
    SELECT COUNT(*) FROM address_book a JOIN k6_cleanup_users t ON t.id = a.user_id
);
SET @carts = (
    SELECT COUNT(*) FROM shopping_cart s JOIN k6_cleanup_users t ON t.id = s.user_id
);
SET @blockers = @foreign_holders + @foreign_buyers + @other_user_coupons + @other_coupon_orders
              + @paid_orders + @used_coupons + @use_records + @user_use_records + @food_orders + @addresses + @carts;

-- 先看这些输出。blockers必须为0；大于0时后面的DELETE均不会删除任何数据。
SELECT @apply AS apply_deletion, @run_id AS selected_run,
       @blockers AS blockers,
       @foreign_holders AS other_users_holding_test_coupons,
       @foreign_buyers AS other_users_buying_test_coupons,
       @other_user_coupons AS test_users_holding_other_coupons,
       @other_coupon_orders AS test_users_buying_other_coupons,
       @paid_orders AS paid_or_refunded_coupon_orders,
       @used_coupons AS locked_used_or_refunded_coupons,
       @use_records AS coupon_use_records, @user_use_records AS user_coupon_use_records,
       @food_orders AS food_orders, @addresses AS addresses, @carts AS shopping_carts;

-- 分开查询，避免MySQL在同一条UNION中重复引用临时表时报告Can't reopen table。
SELECT 'user' AS target_table, COUNT(*) AS candidate_rows FROM k6_cleanup_users;
SELECT 'coupon' AS target_table, COUNT(*) AS candidate_rows FROM k6_cleanup_coupons;
SELECT 'coupon_order' AS target_table, COUNT(*) AS candidate_rows FROM coupon_order
    WHERE coupon_id IN (SELECT id FROM k6_cleanup_coupons) AND user_id IN (SELECT id FROM k6_cleanup_users);
SELECT 'user_coupon' AS target_table, COUNT(*) AS candidate_rows FROM user_coupon
    WHERE coupon_id IN (SELECT id FROM k6_cleanup_coupons) AND user_id IN (SELECT id FROM k6_cleanup_users);
SELECT id, name FROM k6_cleanup_coupons ORDER BY id;

-- 先删除测试券的关联记录，再删除专用券和专用用户。所有DELETE都有双重开关。
DELETE FROM coupon_order
WHERE coupon_id IN (SELECT id FROM k6_cleanup_coupons)
  AND user_id IN (SELECT id FROM k6_cleanup_users) AND @apply = 1 AND @blockers = 0;
DELETE FROM user_coupon
WHERE coupon_id IN (SELECT id FROM k6_cleanup_coupons)
  AND user_id IN (SELECT id FROM k6_cleanup_users) AND @apply = 1 AND @blockers = 0;
DELETE FROM coupon
WHERE id IN (SELECT id FROM k6_cleanup_coupons) AND @apply = 1 AND @blockers = 0;
DELETE FROM `user`
WHERE id IN (SELECT id FROM k6_cleanup_users) AND @apply = 1 AND @blockers = 0;

COMMIT;
DROP TEMPORARY TABLE k6_cleanup_coupons;
DROP TEMPORARY TABLE k6_cleanup_users;
