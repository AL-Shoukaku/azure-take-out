-- 有资格返回 0，库存不足返回 -1，已经领取过返回 -2，有key不存在返回 -3
local user_id = ARGV[1]
local stock_key = KEYS[1]
local user_key = KEYS[2]

if redis.call("EXISTS", stock_key) == 0 or redis.call("EXISTS", user_key) == 0 then
    return -3 -- key 不存在
end


if redis.call("SISMEMBER", user_key, user_id) == 1 then
    return -2 -- 已经领取过
end

local stock = tonumber(redis.call("GET", stock_key))
if stock <= 0 then
    return -1 -- 库存不足
end


redis.call("DECR", stock_key) -- 扣减库存
redis.call("SADD", user_key, user_id) -- 记录用户领取记录
return 0 -- 有资格领取

