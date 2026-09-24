-- 可重入分布式锁：获取（HINCRBY 计数 + PEXPIRE 续租）
-- KEYS[1] = 锁 key；ARGV[1] = 持有者 token；ARGV[2] = 租约毫秒
local token = redis.call('HGET', KEYS[1], 'token')
if not token then
  redis.call('HSET', KEYS[1], 'token', ARGV[1], 'count', 1)
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
  return 1
elseif token == ARGV[1] then
  redis.call('HINCRBY', KEYS[1], 'count', 1)
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
  return 1
else
  return 0
end
