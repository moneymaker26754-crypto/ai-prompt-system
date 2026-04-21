---@diagnostic disable: undefined-global
local result = {}

for i = 1, #KEYS do
    local snap = KEYS[i]
    --读取当前key的增量快照值
    local value = redis.call('GET', snap)
    if not value then
        value = '0'
    end
    table.insert(result, value)
end
return result
