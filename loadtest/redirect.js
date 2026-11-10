// k6 load test for the redirect hot path.
//
// Start the app with TRUST_PROXY=true so the X-Forwarded-For header below is honoured.
// Without it every request looks like one client and the rate limiter (correctly)
// answers 429 once that client's bucket is empty.
//
//   TRUST_PROXY=true docker compose up --build

//   k6 run loadtest/redirect.js
//   k6 run -e BASE=http://localhost:8080 loadtest/redirect.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';

export const options = {
  vus: 50,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<50'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/api/v1/urls`,
    JSON.stringify({ url: 'https://example.com/load-test' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  return { code: res.json('shortCode') };
}

export default function (data) {
  // A distinct "client" per VU/iteration so each one gets its own token bucket.
  const res = http.get(`${BASE}/${data.code}`, {
    redirects: 0,
    headers: { 'X-Forwarded-For': `10.${__VU}.${__ITER % 250}.1` },
  });
  check(res, { 'is 302': (r) => r.status === 302 });
}
