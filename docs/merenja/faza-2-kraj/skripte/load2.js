import http from 'k6/http';
import { check } from 'k6';

// Uopstena verzija load.js iz faze 1: podrzava metod, token i telo zahteva,
// da bi se istim alatom merile i zasticene rute i prijava.
const BASE   = __ENV.BASE || 'http://127.0.0.1:8080';
const METHOD = (__ENV.METHOD || 'GET').toUpperCase();
const TARGET = __ENV.TARGET_PATH;
const TOKEN  = __ENV.TOKEN || '';
const BODY   = __ENV.BODY || null;
const EXPECT = Number(__ENV.EXPECT || 200);

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const params = { headers: {} };
  if (TOKEN) params.headers['Authorization'] = `Bearer ${TOKEN}`;
  if (BODY)  params.headers['Content-Type'] = 'application/json';
  const res = http.request(METHOD, `${BASE}${TARGET}`, BODY, params);
  check(res, { [`status ${EXPECT}`]: (r) => r.status === EXPECT });
}

export function handleSummary(data) {
  return { [__ENV.OUT || 'summary.json']: JSON.stringify(data, null, 2) };
}
