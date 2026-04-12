---@diagnostic disable: undefined-global
local result = {}

for i = 1, #KEYS do
    local key = KEYS[i]
    local deduct = tonumber(ARGV[i]) or 0
    local current = tonumber(redis.call('GET', key) or '0')
    --如果后来又新增了快照值，就能保留新增的部分
    local nextValue = current - deduct
    if nextValue < 0 then
        nextValue = 0
    end

    redis.call('SET', key, tostring(nextValue), 'EX', 3600)
    table.insert(result, tostring(nextValue))
end
return result
