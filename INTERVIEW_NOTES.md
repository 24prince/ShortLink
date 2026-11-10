# Interview notes (not part of the public README; keep for yourself)

Know every answer below in your own words. If you cannot explain a decision, do not claim it.

## 60-second pitch
"I built a URL shortener in Spring Boot with MySQL and Redis. The redirect path is cache-aside, so
MySQL is only hit on a miss. Click counts are written behind: a Redis INCR on the hot path and a
scheduled batch flush to MySQL. I also built a distributed rate limiter, a token bucket implemented
as an atomic Redis Lua script, so limits hold across multiple instances. If Redis goes down, the
service degrades instead of failing: reads fall back to MySQL and the limiter fails open."

## Likely questions

**Why a Lua script instead of GET then SET?**
Two instances could both read "1 token left" and both allow the request. Redis executes a script
atomically, so read, refill, decide and write happen as one step.

**Why token bucket and not fixed window or sliding window?**
Fixed window allows 2x the limit at a window boundary. Sliding-window log is exact but stores one
entry per request. Token bucket is O(1) memory per client, allows controlled bursts and gives a
natural `Retry-After`.

**Why use Redis TIME inside the script?**
App servers' clocks drift. Using one time source (Redis) removes clock skew between instances.

**What if Redis dies?**
Redirects fall back to MySQL (slower but correct). Click counts go straight to MySQL. The limiter
fails open, trading protection for availability; for a security-critical limit I would fail closed
or use a local in-memory fallback limiter.

**Why write-behind for clicks? What can go wrong?**
A DB UPDATE per redirect serialises on the row lock for popular links. Risk: if Redis loses its
data before a flush, up to one flush interval of clicks is lost. Mitigations: short interval,
Redis AOF persistence, restore-on-failure logic (implemented). Stats are eventually consistent.

**How do you generate short codes? Collisions?**
Base62 of the auto-increment id plus an offset. Unique ids give unique codes. The only collision
risk is a custom alias equal to a future generated code, handled by skipping to the next id.
Downside: sequential codes are guessable; fix by permuting the id before encoding.

**How would you scale this?**
Reads: app instances are stateless, Redis absorbs hot keys, add read replicas. Writes: a dedicated
ID service (Snowflake-style) or range allocation instead of one auto-increment, then shard by code.
Redis Cluster for cache and limiter keys.

**Why 302 and not 301?** Browsers cache 301 forever: no analytics, expiry would not work.

**Why is the column `ascii_bin`?** Default MySQL collation is case-insensitive; Base62 is not.

**What would you do differently with more time?**
Negative caching, expired-row cleanup job, auth, Redis Sentinel, distributed tracing, and a proper
benchmark with published numbers.

## Be honest about
- It is a single-service project; it has not served real traffic.
- Only claim performance numbers you measured yourself with `loadtest/redirect.js`.
