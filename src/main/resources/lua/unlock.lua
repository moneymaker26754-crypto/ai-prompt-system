---@diagnostic disable: undefined-global
local current = redis.call('GET', KEYS[1])
--当前key值的UUID与传入的UUID相同才删除锁
if current == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0