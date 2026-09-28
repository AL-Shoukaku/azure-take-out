-- 已有数据库执行一次；新建数据库直接使用 db/sky.sql。
-- 执行前确认以下两条查询均无结果，否则先处理历史订单号。
-- SELECT id FROM orders WHERE number IS NULL;
-- SELECT number, COUNT(*) FROM orders GROUP BY number HAVING COUNT(*) > 1;

ALTER TABLE orders
    MODIFY COLUMN number varchar(50) COLLATE utf8_bin NOT NULL COMMENT '订单号',
    ADD UNIQUE KEY uk_orders_number (number);
