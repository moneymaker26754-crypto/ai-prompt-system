-- 可重入分布式锁：释放（校验 token 防误删；计数归零后 DEL）
-- KEYS[1] = 锁 key；ARGV[1] = 持有者 token；ARGV[2] = 租约毫秒
if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then
  return 0
end
local count = redis.call('HINCRBY', KEYS[1], 'count', -1)
if count <= 0 then
  redis.call('DEL', KEYS[1])
  return 1
else
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
  return 1
end
