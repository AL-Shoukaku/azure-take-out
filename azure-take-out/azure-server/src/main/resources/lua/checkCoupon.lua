-- 预扣成功返回 0，库存不足返回 -1，已经领取过返回 -2，抢券缓存不可用返回 -3
local user_id = ARGV[1]
local order_number = ARGV[2] -- 领券时不传
local stock_key = KEYS[1]
local user_key = KEYS[2]
local order_key = KEYS[3]

if redis.call("EXISTS", unpack(KEYS)) ~= #KEYS then
    return -3 -- key 不存在
end

if redis.call("SISMEMBER", user_key, user_id) == 1 then
    return -2 -- 已经领取过
end

local stock = tonumber(redis.call("GET", stock_key))
if stock <= 0 then
    return -1 -- 库存不足
end

if order_number then
    redis.call("SADD", order_key, order_number)
end
redis.call("DECR", stock_key)
redis.call("SADD", user_key, user_id)
return 0
