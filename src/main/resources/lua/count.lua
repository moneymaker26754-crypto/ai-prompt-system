local result = {}

for i = 1, #KEYS, 2 do
	local liveKey = KEYS[i]
	local processingKey = KEYS[i + 1]

	local value = redis.call('GET', liveKey)
	if not value then
		value = '0'
	end
	redis.call('SET', liveKey, '0')
	redis.call('SET', processingKey, value)

	table.insert(result, value)
end
return result