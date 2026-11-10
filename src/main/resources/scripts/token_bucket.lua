-- Atomic token-bucket rate limiter.
-- KEYS[1] : bucket key
-- ARGV[1] : capacity (max tokens / burst size)
-- ARGV[2] : refill rate (tokens per second)
-- ARGV[3] : tokens requested
-- Returns : { allowed (1/0), tokens remaining (floored), retry-after seconds }
--
-- Time comes from Redis (TIME), not from the app servers, so clock skew
-- between application instances cannot let a client get extra tokens.
local key       = KEYS[1]
local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local t   = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)   -- ms

local data   = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts     = tonumber(data[2])

if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

local elapsed = math.max(0, now - ts) / 1000.0
tokens = math.min(capacity, tokens + elapsed * rate)

local allowed = 0
local retry_after = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_after = math.ceil((requested - tokens) / rate)
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
-- Idle buckets expire on their own: twice the time needed to refill from empty.
redis.call('PEXPIRE', key, math.ceil(capacity / rate * 1000) * 2)

return { allowed, math.floor(tokens), retry_after }
