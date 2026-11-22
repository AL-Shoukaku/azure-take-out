-- KEYS 依次为库存 key、用户抢券 Set。购券另传订单号 Set。
-- ARGV 依次为用户 ID、预扣标记。
-- 1 表示已恢复，0 表示没有这笔预扣，-3 表示缓存缺失或用户标记异常。
local stock_key = KEYS[1]
local user_key = KEYS[2]
local reservation_key = KEYS[3] or user_key
local user_id = ARGV[1]
local reservation = ARGV[2]

if redis.call("EXISTS", unpack(KEYS)) ~= #KEYS then
    return -3
end

-- 预扣标记只对应本次请求，重复补偿或旧请求补偿直接结束。
if reservation == "-1" or redis.call("SISMEMBER", reservation_key, reservation) == 0 then
    return 0
end

if user_id == "-1" or redis.call("SISMEMBER", user_key, user_id) == 0 then
    return -3
end

redis.call("INCR", stock_key)
redis.call("SREM", user_key, user_id)
redis.call("SREM", reservation_key, reservation)
return 1
