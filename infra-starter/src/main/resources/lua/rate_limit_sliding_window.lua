-- 滑动窗口限流：ZSET 原子判定（ZREMRANGEBYSCORE 清理过期成员 + ZCARD 计数）
-- KEYS[1] = 限流 key；ARGV[1] = 窗口毫秒；ARGV[2] = limit；ARGV[3] = 当前毫秒；ARGV[4] = 唯一成员标识
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3] - ARGV[1])
local count = redis.call('ZCARD', KEYS[1])
if count >= tonumber(ARGV[2]) then
  return 0
end
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
redis.call('PEXPIRE', KEYS[1], ARGV[1] + 1000)
return 1
