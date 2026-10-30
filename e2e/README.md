# E2E API Test Suite

One-file, zero-dependency (Node ≥ 18, built-in `fetch`) end-to-end suite that
exercises the live test server over real HTTP: authentication, questionnaire
dispatch → answer → risk banding, the high-risk list, the referral lifecycle,
doctor diagnosis propagation, an id-card uniqueness regression, a concurrency
smoke test, and notifications.

## Run

```bash
node e2e/run-e2e.mjs                      # default target http://47.117.156.53:7080
node e2e/run-e2e.mjs http://host:port     # custom target
```

Exit code `0` = no failures, `1` = at least one FAIL, `2` = runner crash.
Results are written to `e2e/report.json` and `e2e/report.md` on every run.

## Coverage

| Scenario | What it proves |
|---|---|
| S1 auth & permissions | All five roles log in; wrong/unknown credentials rejected; teacher cannot write diagnoses (403); students cannot read the high-risk list |
| S2 dispatch | Teacher dispatch reaches the student's scale list |
| S3 low risk | Draft save works; minimal answers submit COMPLETED; low-risk student absent from the strict high-risk list |
| S4 medium band | phq9 = 10 (item 9 = 0) → MEDIUM: hidden without `includeMedium`, visible with it |
| S5 acute override | phq9_9 = 1 with an otherwise zero score → HIGH + `SUICIDAL_IDEATION`, HIGH entries sorted before MEDIUM |
| S6 referral lifecycle | Create → approve → assign doctor → schedule → student sees the referral and its step timeline |
| S7 diagnosis | Doctor writes SCID diagnosis; the student's referral details and the high-risk list show it immediately; teacher blocked (in S1) |
| S8 id-card regression | Two students submitting the same `demo_id_card` both get 200 (previous production defect: unique-constraint 500) |
| S9 concurrency smoke | 8 parallel sessions (login + scale detail + draft) all succeed |
| S10 notifications | HC notification endpoint healthy after a high-risk submission (alert presence is a soft check) |

## Safety and re-runnability

- Never restarts containers, truncates, reseeds, or deletes anything; it only
  creates assignments/submissions/referrals/diagnoses for the dedicated
  student accounts `S2023006`–`S2023010`.
- Submissions are idempotent: re-submitting a completed scale returns
  `COMPLETED` without error, so the suite can run repeatedly.
- Referral steps that depend on server-side state the suite cannot guarantee
  (e.g. a hospital without a trial admin) are recorded as **SKIP** with the
  API's response, not as failures.

## Known caveats

- The suite assumes the seeded demo dataset (accounts above, hospital +
  trial admin + doctor). After a backend restart the seeder recreates it;
  if the seeder is disabled (`SEED_DEMO_DATA=false`) the accounts may not
  exist and S1 will fail — that is a data problem, not a product bug.
- S6 assigns whatever doctor `/api/doctors` lists first; if that doctor
  lacks a department profile the backend rejects assignment and the step is
  SKIPped with the reason.
