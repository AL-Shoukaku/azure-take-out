-- key 缺失时不重建；恢复库存时保留原有过期时间。
local key = KEYS[1]
if redis.call("EXISTS", key) == 0 then
    return 0
end
redis.call("INCR", key)
return 1
