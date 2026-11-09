local key = KEYS[1]
local value = ARGV[1]
local expire_time = tonumber(ARGV[2])
redis.call("SADD", key, value)
redis.call("EXPIRE", key, expire_time)
return 0