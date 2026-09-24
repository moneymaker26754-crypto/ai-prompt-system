-- 可重入分布式锁：看门狗续租（仅当 token 仍为持有者时续期，防止续错人）
-- KEYS[1] = 锁 key；ARGV[1] = 持有者 token；ARGV[2] = 租约毫秒
if redis.call('HGET', KEYS[1], 'token') == ARGV[1] then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
  return 1
end
return 0
