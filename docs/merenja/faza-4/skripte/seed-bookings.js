import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// Puni bazu rezervacijama za merenje globalnog pregleda. Svaka iteracija je jedna
// rezervacija od 30 minuta: redom slotovi dana, pa resursi, pa dani, pocev od DAY0
// (08:00 lokalno, prvi dan). OFFSET nastavlja numeraciju posle prethodnog punjenja,
// pa se termini nikad ne preklapaju.
const BASE   = __ENV.BASE || 'http://127.0.0.1:8080';
const TOKENS = __ENV.TOKENS.split(',');
const RES    = __ENV.RES_IDS.split(',');
const DAY0   = Number(__ENV.DAY0);
const OFFSET = Number(__ENV.OFFSET || 0);
const SLOT   = 30 * 60 * 1000;
const SLOTS  = 24;

export const options = {
  scenarios: {
    seed: { executor: 'shared-iterations', vus: 10, iterations: Number(__ENV.COUNT), maxDuration: '10m' },
  },
  summaryTrendStats: ['avg', 'med', 'p(99)', 'max'],
};

export default function () {
  const i = OFFSET + exec.scenario.iterationInTest;
  const perDay = RES.length * SLOTS;
  const day = Math.floor(i / perDay);
  const r = Math.floor((i % perDay) / SLOTS);
  const slot = i % SLOTS;
  const start = DAY0 + day * 86400000 + slot * SLOT;
  const body = JSON.stringify({ resourceId: RES[r], startTime: start, endTime: start + SLOT });
  const res = http.post(`${BASE}/api/bookings`, body, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKENS[i % TOKENS.length]}` },
  });
  check(res, { 'status 201': (x) => x.status === 201 });
}

export function handleSummary(data) {
  return { [__ENV.OUT || 'seed.json']: JSON.stringify(data, null, 2) };
}
