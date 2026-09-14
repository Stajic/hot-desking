import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://127.0.0.1:8080';
const TARGET = __ENV.TARGET_PATH;

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // bez thresholds - ovo je merenje, ne prolaz/pad test
};

export default function () {
  const res = http.get(`${BASE}${TARGET}`);
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const out = __ENV.OUT || 'summary.json';
  return { [out]: JSON.stringify(data, null, 2) };
}
