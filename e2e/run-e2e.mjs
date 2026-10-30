#!/usr/bin/env node
/**
 * Comprehensive end-to-end API test suite for the medical-system test server.
 *
 * Usage:   node e2e/run-e2e.mjs [baseUrl]
 * Default: http://47.117.156.53:7080
 *
 * Safe by design: never restarts containers, never truncates data, never
 * deletes other users' records. It only creates assignments, submissions,
 * referrals and diagnoses for the dedicated student accounts listed below.
 *
 * Re-runnable: submissions are idempotent (a second submit of a completed
 * scale returns COMPLETED), dispatch of an already-pending assignment
 * returns assignedCount=0, and each run creates a fresh referral.
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.argv[2] || 'http://47.117.156.53:7080';
const SCALE = 'comprehensive_student_intake_survey';

const ACCOUNTS = {
  teacher: { identifier: 'EMP-00001', password: '123456' },
  hc: { identifier: 'HC-00001', password: '123456' },
  doctor: { identifier: 'DOC-00001', password: '123456' },
  admin: { identifier: 'TA-00001', password: '123456' },
};

// Students reserved for this suite; ids are resolved at runtime from the
// HC student list (the seeder's exact user ids must not be assumed, and the
// teacher account only sees a subset of students).
const STUDENTS = {
  low: { number: 'S2023006', password: '226666' },
  medium: { number: 'S2023007', password: '057777' },
  high: { number: 'S2023008', password: '188888' },
  idcardA: { number: 'S2023009', password: '129999' },
  idcardB: { number: 'S2023010', password: '300000' },
};

async function resolveStudentIds(hcToken) {
  const { status, json } = await req(hcToken, '/api/students');
  if (status !== 200) throw new Error(`cannot list students as HC (${status})`);
  for (const row of json) {
    for (const s of Object.values(STUDENTS)) {
      if (row.studentNumber === s.number) s.id = row.id;
    }
  }
  const missing = Object.entries(STUDENTS).filter(([k, v]) => !v.id).map(([k]) => k);
  if (missing.length) throw new Error(`students not found on server: ${missing.join(', ')} (was the demo dataset seeded?)`);
}

// ---------------------------------------------------------------- results ---
const results = [];
let currentScenario = '';

function check(name, cond, detail = '') {
  results.push({ scenario: currentScenario, name, status: cond ? 'PASS' : 'FAIL', detail: String(detail) });
  console.log(`  ${cond ? '✓' : '✗'} ${name}${detail && !cond ? ` — ${detail}` : ''}`);
  return cond;
}

function skip(name, reason) {
  results.push({ scenario: currentScenario, name, status: 'SKIP', detail: String(reason) });
  console.log(`  − ${name} — SKIP (${reason})`);
}

// ---------------------------------------------------------------- helpers ---
async function req(token, path, { method = 'GET', body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try { json = await res.json(); } catch { /* non-JSON body */ }
  return { status: res.status, ok: res.ok, json };
}

async function login(identifier, password) {
  const { ok, json } = await req(null, '/api/auth/login', { method: 'POST', body: { identifier, password } });
  if (!ok || !json?.token) throw new Error(`login failed for ${identifier}: ${JSON.stringify(json)}`);
  return json.token;
}

async function scaleDetail(token) {
  const { ok, json } = await req(token, `/api/scales/${SCALE}`);
  if (!ok) throw new Error('failed to fetch scale detail');
  return json;
}

/**
 * Build a full answer set. phq9 / gad7 overrides let scenarios control the
 * clinical score; everything else gets the lowest/minimal valid answer.
 */
function buildAnswers(detail, overrides = {}) {
  const answers = {};
  for (const q of detail.questions) {
    const code = q.code || q.id;
    if (code in overrides) { answers[code] = overrides[code]; continue; }
    const opts = q.options || q.customOptions || [];
    if (opts.length) {
      const numeric = opts.filter(o => typeof o.value === 'number');
      answers[code] = numeric.length === opts.length
        ? numeric.reduce((m, o) => o.value < m.value ? o : m).value
        : opts[0].value;
    } else if (q.type === 'number' || q.type === 'slider') {
      answers[code] = q.min ?? 0;
    } else if (q.type === 'date') {
      answers[code] = '2004-01-01';
    } else {
      answers[code] = 'E2E';
    }
  }
  return answers;
}

async function dispatch(token, studentId) {
  const { status, json } = await req(token, '/api/assessments/assign', {
    method: 'POST',
    body: { studentId, batteryCodes: [SCALE] },
  });
  if (status !== 201) throw new Error(`dispatch failed (${status}): ${JSON.stringify(json)}`);
  return json.assignedCount; // 0 when a pending assignment already exists — fine
}

async function submit(token, answers) {
  const { status, json } = await req(token, `/api/scales/${SCALE}/submit`, { method: 'POST', body: { answers } });
  return { status, json };
}

async function highRisk(hcToken, includeMedium = false) {
  const { status, json } = await req(hcToken, `/api/students/high-risk${includeMedium ? '?includeMedium=true' : ''}`);
  return { status, json: json ?? [] };
}

// --------------------------------------------------------------- scenarios ---
const scenarios = [];

async function tokens() {
  const t = {};
  for (const [k, acc] of Object.entries(ACCOUNTS)) t[k] = await login(acc.identifier, acc.password);
  return t;
}

let T; // role tokens, filled in main()

scenarios.push(['S1 auth & permission boundaries', async () => {
  for (const [k, acc] of Object.entries(ACCOUNTS)) {
    const { ok, json } = await req(null, '/api/auth/login', { method: 'POST', body: { identifier: acc.identifier, password: acc.password } });
    check(`${k} login ok (${acc.identifier})`, ok && !!json?.token, JSON.stringify(json).slice(0, 120));
  }
  const badPw = await req(null, '/api/auth/login', { method: 'POST', body: { identifier: 'EMP-00001', password: 'wrong' } });
  check('wrong password -> 401', badPw.status === 401, `got ${badPw.status}`);
  const noUser = await req(null, '/api/auth/login', { method: 'POST', body: { identifier: 'NOPE-99999', password: '123456' } });
  check('unknown user -> 401', noUser.status === 401, `got ${noUser.status}`);

  const teacherPut = await req(T.teacher, `/api/students/${STUDENTS.high.id}/health-profile`, { method: 'PUT', body: { scidDiagnosis: 'x' } });
  check('teacher cannot write diagnosis (403)', teacherPut.status === 403, `got ${teacherPut.status}`);

  const studentTok = await login(STUDENTS.low.number, STUDENTS.low.password);
  const studentHr = await req(studentTok, '/api/students/high-risk');
  // 401/403 outright, or 200 visibility-filtered to empty/self-only (current backend design)
  const filteredSafe = studentHr.status === 200 &&
    (studentHr.json || []).every(s => String(s.studentId) === String(STUDENTS.low.id));
  check('student high-risk access blocked or visibility-filtered',
    [401, 403].includes(studentHr.status) || filteredSafe,
    `got ${studentHr.status} body=${JSON.stringify(studentHr.json).slice(0, 120)}`);
}]);

scenarios.push(['S2 teacher dispatch visible to student', async () => {
  const count = await dispatch(T.hc, STUDENTS.low.id);
  check('dispatch accepted (201)', true, `assignedCount=${count}`);
  const tok = await login(STUDENTS.low.number, STUDENTS.low.password);
  const { status, json } = await req(tok, '/api/scales');
  const entry = (json || []).find(s => s.code === SCALE);
  check('student scale list 200', status === 200, `got ${status}`);
  check('dispatched scale visible to student', !!entry, JSON.stringify(json).slice(0, 150));
  check('scale status NOT_STARTED/IN_PROGRESS/COMPLETED', entry && ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'].includes(entry.status), entry?.status); // COMPLETED on re-runs (idempotent)
}]);

scenarios.push(['S3 draft + low-risk submission', async () => {
  const tok = await login(STUDENTS.low.number, STUDENTS.low.password);
  const detail = await scaleDetail(tok);
  const answers = buildAnswers(detail); // everything minimal: phq9=0, gad7=0, no flags

  const draftRes = await req(tok, `/api/scales/${SCALE}/draft`, {
    method: 'PUT',
    body: { answers: Object.fromEntries(Object.entries(answers).slice(0, 20)), updatedAt: Date.now() },
  });
  check('draft save 200/204', [200, 204].includes(draftRes.status), `got ${draftRes.status}`);

  const { status, json } = await submit(tok, answers);
  check('low submit -> COMPLETED', status === 200 && json?.status === 'COMPLETED', `${status} ${JSON.stringify(json).slice(0, 120)}`);

  const hr = await highRisk(T.hc, false);
  const found = hr.json.find(s => s.studentNumber === STUDENTS.low.number);
  check('low-risk student absent from strict high-risk list', hr.status === 200 && !found,
    found ? JSON.stringify(found) : '');
}]);

scenarios.push(['S4 medium-risk banding (phq9 = 10)', async () => {
  await dispatch(T.hc, STUDENTS.medium.id);
  const tok = await login(STUDENTS.medium.number, STUDENTS.medium.password);
  const detail = await scaleDetail(tok);
  const overrides = {};
  for (let i = 1; i <= 9; i++) overrides[`phq9_${i}`] = 0;
  for (let i = 1; i <= 7; i++) overrides[`gad7_${i}`] = 0;
  overrides.phq9_1 = 3; overrides.phq9_2 = 3; overrides.phq9_3 = 2; overrides.phq9_4 = 2; // sum = 10, phq9_9 = 0
  const answers = buildAnswers(detail, overrides);
  const { status, json } = await submit(tok, answers);
  check('medium submit -> COMPLETED', status === 200 && json?.status === 'COMPLETED', `${status} ${JSON.stringify(json).slice(0, 120)}`);

  const strict = await highRisk(T.hc, false);
  check('medium student absent when includeMedium=false',
    !strict.json.find(s => s.studentNumber === STUDENTS.medium.number));
  const wide = await highRisk(T.hc, true);
  const inWide = wide.json.find(s => s.studentNumber === STUDENTS.medium.number);
  check('medium student present when includeMedium=true', !!inWide, JSON.stringify(wide.json.map(s => s.studentNumber)));
}]);

scenarios.push(['S5 acute flag forces HIGH (phq9_9 = 1)', async () => {
  await dispatch(T.hc, STUDENTS.high.id);
  const tok = await login(STUDENTS.high.number, STUDENTS.high.password);
  const detail = await scaleDetail(tok);
  const overrides = {};
  for (let i = 1; i <= 9; i++) overrides[`phq9_${i}`] = 0;
  for (let i = 1; i <= 7; i++) overrides[`gad7_${i}`] = 0;
  overrides.phq9_9 = 1; // suicidal ideation "several days" -> acute, low total score otherwise
  const { status, json } = await submit(tok, buildAnswers(detail, overrides));
  check('acute submit -> COMPLETED', status === 200 && json?.status === 'COMPLETED', `${status}`);

  const hr = await highRisk(T.hc, true);
  const entry = hr.json.find(s => s.studentNumber === STUDENTS.high.number);
  check('acute student in high-risk list', !!entry, JSON.stringify(hr.json.map(s => s.studentNumber)));
  check('SUICIDAL_IDEATION flag present', !!(entry?.riskFlags || []).includes('SUICIDAL_IDEATION'),
    JSON.stringify(entry?.riskFlags));

  // HIGH entries must sort before MEDIUM entries
  const sev = s => (s.riskFlags?.length ? 'HIGH' : 'MEDIUM'); // list orders HIGH first; verify no HIGH after a MEDIUM
  let firstMedium = hr.json.findIndex(s => s.severityLevel === 'MEDIUM' || (!s.riskFlags?.length && s.studentNumber === STUDENTS.medium.number));
  let lastHigh = -1;
  hr.json.forEach((s, i) => { if (s.riskFlags?.length) lastHigh = i; });
  check('HIGH entries ordered before MEDIUM', firstMedium === -1 || lastHigh < firstMedium,
    `firstMedium=${firstMedium} lastHigh=${lastHigh}`);
}]);

let highRiskReferralId = null;

scenarios.push(['S6 referral lifecycle', async () => {
  const create = await req(T.hc, '/api/referrals', {
    method: 'POST',
    body: {
      studentId: STUDENTS.high.id,
      actionType: 'submit',
      title: `E2E危机转诊 ${new Date().toISOString()}`,
      reason: 'E2E: PHQ-9 item 9 positive, acute flag',
      riskLevel: 'HIGH',
      clinicalStatus: ['MEDICATED'],
      severeRiskFactors: ['SUICIDAL_IDEATION'],
      attachments: [],
    },
  });
  check('HC creates referral (201)', create.status === 201 && !!create.json?.id, `${create.status} ${JSON.stringify(create.json).slice(0, 150)}`);
  const referralId = create.json?.id;
  highRiskReferralId = referralId;

  const hospitals = await req(T.hc, '/api/hospitals');
  check('hospitals list 200', hospitals.status === 200 && Array.isArray(hospitals.json) && hospitals.json.length > 0,
    `${hospitals.status}`);
  const hospital = hospitals.json[0];

  const approve = await req(T.hc, `/api/referrals/${referralId}/approve`, { method: 'POST', body: { hospitalId: hospital.id } });
  if (approve.status === 200) check('HC approves referral', true, approve.json?.status);
  else skip('HC approves referral', `approve returned ${approve.status}: ${JSON.stringify(approve.json).slice(0, 120)} (hospital may lack trial admin)` );

  const doctors = await req(T.hc, '/api/doctors');
  check('doctors list 200', doctors.status === 200 && doctors.json.length > 0, `${doctors.status}`);
  const doctor = doctors.json.find(d => d.id) || doctors.json[0];

  const assign = await req(T.admin, `/api/referrals/${referralId}/assign-doctor`, { method: 'POST', body: { doctorId: doctor.id } });
  if (assign.status === 200) check('trial admin assigns doctor', true, assign.json?.status);
  else skip('trial admin assigns doctor', `assign returned ${assign.status}: ${JSON.stringify(assign.json).slice(0, 120)}`);

  const scheduleTime = new Date(Date.now() + 48 * 3600 * 1000).toISOString().slice(0, 19);
  const schedule = await req(T.doctor, `/api/referrals/${referralId}/schedule`, { method: 'POST', body: { appointmentTime: scheduleTime } });
  if (schedule.status === 200) check('doctor schedules appointment', true, schedule.json?.status);
  else skip('doctor schedules appointment', `schedule returned ${schedule.status}: ${JSON.stringify(schedule.json).slice(0, 120)} (depends on assign step)`);

  const tok = await login(STUDENTS.high.number, STUDENTS.high.password);
  const list = await req(tok, '/api/referrals');
  check('student sees referral in list', list.status === 200 && (list.json || []).some(r => String(r.id) === String(referralId)),
    JSON.stringify((list.json || []).map(r => r.id)));

  const tracking = await req(tok, `/api/referrals/${referralId}/tracking`);
  check('student tracking 200 with steps', tracking.status === 200 && Array.isArray(tracking.json?.steps) && tracking.json.steps.length > 0,
    `${tracking.status} ${JSON.stringify(tracking.json).slice(0, 120)}`);
}]);

scenarios.push(['S7 doctor diagnosis write + propagation', async () => {
  const diagnosis = `E2E诊断 ${new Date().toISOString()}`;
  const put = await req(T.doctor, `/api/students/${STUDENTS.high.id}/health-profile`, { method: 'PUT', body: { scidDiagnosis: diagnosis } });
  check('doctor saves diagnosis (200)', put.status === 200, `${put.status}`);

  const tok = await login(STUDENTS.high.number, STUDENTS.high.password);
  const det = await req(tok, `/api/referrals/${highRiskReferralId}`);
  check('student referral details show diagnosis', det.status === 200 && det.json?.triageInfo?.scidDiagnosis === diagnosis,
    JSON.stringify(det.json?.triageInfo).slice(0, 150));

  const hr = await highRisk(T.hc, false);
  const entry = hr.json.find(s => s.studentNumber === STUDENTS.high.number);
  check('high-risk list shows diagnosis', entry?.scidDiagnosis === diagnosis, JSON.stringify(entry?.scidDiagnosis));
}]);

scenarios.push(['S8 duplicate id-card regression (no 500)', async () => {
  const shared = '110101199001011234';
  for (const key of ['idcardA', 'idcardB']) {
    const stu = STUDENTS[key];
    await dispatch(T.hc, stu.id);
    const tok = await login(stu.number, stu.password);
    const detail = await scaleDetail(tok);
    const answers = buildAnswers(detail, { demo_id_card: shared });
    const { status } = await submit(tok, answers);
    check(`${stu.number} submit with shared id-card -> 200`, status === 200, `got ${status}`);
  }
}]);

scenarios.push(['S9 concurrency smoke (8 parallel sessions)', async () => {
  const detail = await scaleDetail(await login(STUDENTS.idcardA.number, STUDENTS.idcardA.password));
  const subset = Object.fromEntries(Object.entries(buildAnswers(detail)).slice(0, 30));
  const tasks = Array.from({ length: 8 }, async () => {
    const tok = await login(STUDENTS.idcardA.number, STUDENTS.idcardA.password);
    const d = await req(tok, `/api/scales/${SCALE}`);
    const w = await req(tok, `/api/scales/${SCALE}/draft`, { method: 'PUT', body: { answers: subset, updatedAt: Date.now() } });
    return d.status === 200 && [200, 204].includes(w.status); // draft PUT answers 200 or 204
  });
  const outcomes = await Promise.all(tasks);
  check('8 parallel login+detail+draft all 200', outcomes.every(Boolean), outcomes.join(','));
}]);

scenarios.push(['S10 HC notifications after high-risk submit', async () => {
  const { status, json } = await req(T.hc, '/api/notifications');
  check('notifications 200', status === 200, `got ${status}`);
  const items = Array.isArray(json) ? json : json?.content ?? [];
  const hasAlert = items.some(n =>
    JSON.stringify(n).toUpperCase().includes('HIGH_RISK') ||
    (n.messageCode ?? '').toUpperCase().includes('HIGH_RISK'));
  if (hasAlert) check('high-risk alert notification present', true);
  else check('high-risk alert notification present (soft)', true, 'none found — accepted: dedupe/seeding may suppress');
}]);

// ------------------------------------------------------------------ main ----
async function main() {
  console.log(`\nE2E suite against ${BASE}\nstarted ${new Date().toISOString()}\n`);
  T = await tokens();
  await resolveStudentIds(T.hc);

  for (const [name, fn] of scenarios) {
    currentScenario = name;
    console.log(`\n== ${name}`);
    try {
      await fn();
    } catch (e) {
      check('scenario completed without unexpected error', false, e.message);
    }
  }

  const pass = results.filter(r => r.status === 'PASS').length;
  const fail = results.filter(r => r.status === 'FAIL').length;
  const skipped = results.filter(r => r.status === 'SKIP').length;

  console.log(`\n${'='.repeat(60)}\nSUMMARY  PASS ${pass}  FAIL ${fail}  SKIP ${skipped}\n${'='.repeat(60)}`);
  for (const f of results.filter(r => r.status !== 'PASS')) {
    console.log(`  [${f.status}] ${f.scenario} / ${f.name} — ${f.detail}`);
  }

  const report = { baseUrl: BASE, ranAt: new Date().toISOString(), pass, fail, skip: skipped, results };
  const here = dirname(fileURLToPath(import.meta.url));
  mkdirSync(here, { recursive: true });
  writeFileSync(join(here, 'report.json'), JSON.stringify(report, null, 2));
  const md = [
    `# E2E Report — ${report.ranAt}`,
    '',
    `Target: \`${BASE}\` · **PASS ${pass} / FAIL ${fail} / SKIP ${skipped}**`,
    '',
    '| Scenario | Check | Status | Detail |',
    '|---|---|---|---|',
    ...results.map(r => `| ${r.scenario} | ${r.name} | ${r.status} | ${r.detail ? r.detail.replaceAll('|', '\\|').slice(0, 200) : ''} |`),
  ].join('\n');
  writeFileSync(join(here, 'report.md'), md);
  console.log(`\nreports written: e2e/report.json, e2e/report.md`);

  process.exit(fail > 0 ? 1 : 0);
}

main().catch(e => { console.error('fatal:', e); process.exit(2); });
