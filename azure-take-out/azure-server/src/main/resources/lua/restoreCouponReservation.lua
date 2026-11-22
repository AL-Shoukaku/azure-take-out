-- KEYS 依次为库存 key、用户抢券 Set、购券订单号 Set。
-- ARGV 依次为用户 ID、订单号。
-- 1 表示已恢复，0 表示没有这笔预扣，-3 表示缓存缺失或用户标记异常。
local stock_key = KEYS[1]
local user_key = KEYS[2]
local order_key = KEYS[3]
local user_id = ARGV[1]
local order_number = ARGV[2]

if redis.call("EXISTS", stock_key, user_key, order_key) ~= 3 then
    return -3
end

-- 订单号是这一次预扣的标记，重复补偿或旧订单补偿直接结束。
if order_number == "-1" or redis.call("SISMEMBER", order_key, order_number) == 0 then
    return 0
end

if user_id == "-1" or redis.call("SISMEMBER", user_key, user_id) == 0 then
    return -3
end

redis.call("INCR", stock_key)
redis.call("SREM", user_key, user_id)
redis.call("SREM", order_key, order_number)
return 1
