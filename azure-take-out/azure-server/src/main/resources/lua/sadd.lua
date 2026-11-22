local value = ARGV[1]
local expire_time = tonumber(ARGV[2])
for _, key in ipairs(KEYS) do
    redis.call("SADD", key, value)
    redis.call("EXPIRE", key, expire_time)
end
return 0