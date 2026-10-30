# E2E Report — 2026-10-30T07:01:08.485Z

Target: `http://47.117.156.53:7080` · **PASS 38 / FAIL 0 / SKIP 0**

| Scenario | Check | Status | Detail |
|---|---|---|---|
| S1 auth & permission boundaries | teacher login ok (EMP-00001) | PASS | {"token":"eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwicm9sZSI6IlRFQUNIRVIiLCJpYXQiOjE3OTMzNDM2NjAsImV4cCI6MTc5Mzk0ODQ2MCwic3R1Z |
| S1 auth & permission boundaries | hc login ok (HC-00001) | PASS | {"token":"eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxNiIsInJvbGUiOiJIRUFEX0NPVU5TRUxMT1IiLCJpYXQiOjE3OTMzNDM2NjAsImV4cCI6MTc5Mzk0O |
| S1 auth & permission boundaries | doctor login ok (DOC-00001) | PASS | {"token":"eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxNCIsInJvbGUiOiJET0NUT1IiLCJpYXQiOjE3OTMzNDM2NjAsImV4cCI6MTc5Mzk0ODQ2MCwic3R1Z |
| S1 auth & permission boundaries | admin login ok (TA-00001) | PASS | {"token":"eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxMyIsInJvbGUiOiJUUklBTF9BRE1JTiIsImlhdCI6MTc5MzM0MzY2MSwiZXhwIjoxNzkzOTQ4NDYxL |
| S1 auth & permission boundaries | wrong password -> 401 | PASS | got 401 |
| S1 auth & permission boundaries | unknown user -> 401 | PASS | got 401 |
| S1 auth & permission boundaries | teacher cannot write diagnosis (403) | PASS | got 403 |
| S1 auth & permission boundaries | student high-risk access blocked or visibility-filtered | PASS | got 200 body=[] |
| S2 teacher dispatch visible to student | dispatch accepted (201) | PASS | assignedCount=1 |
| S2 teacher dispatch visible to student | student scale list 200 | PASS | got 200 |
| S2 teacher dispatch visible to student | dispatched scale visible to student | PASS | [{"code":"comprehensive_student_intake_survey","title":"中南大学学生心身健康综合调查问卷","subtitle":"2025-2026 学年学生心身健康普查与档案建立","description":"科技部科技创新2030脑科学与类脑研究学生人 |
| S2 teacher dispatch visible to student | scale status NOT_STARTED/IN_PROGRESS/COMPLETED | PASS | COMPLETED |
| S3 draft + low-risk submission | draft save 200/204 | PASS | got 204 |
| S3 draft + low-risk submission | low submit -> COMPLETED | PASS | 200 {"scaleCode":"comprehensive_student_intake_survey","status":"COMPLETED","completedAt":"2026-10-30T07:01:02.541418287"} |
| S3 draft + low-risk submission | low-risk student absent from strict high-risk list | PASS |  |
| S4 medium-risk banding (phq9 = 10) | medium submit -> COMPLETED | PASS | 200 {"scaleCode":"comprehensive_student_intake_survey","status":"COMPLETED","completedAt":"2026-10-30T07:01:03.300800089"} |
| S4 medium-risk banding (phq9 = 10) | medium student absent when includeMedium=false | PASS |  |
| S4 medium-risk banding (phq9 = 10) | medium student present when includeMedium=true | PASS | ["S2023001","S2023005","S2023008","S2023007"] |
| S5 acute flag forces HIGH (phq9_9 = 1) | acute submit -> COMPLETED | PASS | 200 |
| S5 acute flag forces HIGH (phq9_9 = 1) | acute student in high-risk list | PASS | ["S2023001","S2023005","S2023008","S2023007"] |
| S5 acute flag forces HIGH (phq9_9 = 1) | SUICIDAL_IDEATION flag present | PASS | ["SUICIDAL_IDEATION"] |
| S5 acute flag forces HIGH (phq9_9 = 1) | HIGH entries ordered before MEDIUM | PASS | firstMedium=3 lastHigh=2 |
| S6 referral lifecycle | HC creates referral (201) | PASS | 201 {"id":"7","studentId":10,"studentName":"吴东","studentNumber":"S2023008","type":"INITIAL","date":"2026-10-30T07:01:04.446349708","title":"E2E危机转诊 2026-1 |
| S6 referral lifecycle | hospitals list 200 | PASS | 200 |
| S6 referral lifecycle | HC approves referral | PASS | AWAITING_TRIAGE |
| S6 referral lifecycle | doctors list 200 | PASS | 200 |
| S6 referral lifecycle | trial admin assigns doctor | PASS | WAITING_FOR_SCHEDULING |
| S6 referral lifecycle | doctor schedules appointment | PASS | WAITING_FOR_APPOINTMENT |
| S6 referral lifecycle | student sees referral in list | PASS | ["4","5","6","7"] |
| S6 referral lifecycle | student tracking 200 with steps | PASS | 200 {"destination":{"hospital":"中南大学湘雅医院","department":"内科","doctor":"李医生","admin":"张老师","transferDate":null,"appointmentTim |
| S7 doctor diagnosis write + propagation | doctor saves diagnosis (200) | PASS | 200 |
| S7 doctor diagnosis write + propagation | student referral details show diagnosis | PASS | {"priorTherapy":"无","scidDiagnosis":"E2E诊断 2026-10-30T07:01:05.462Z","fullDescription":"E2E: PHQ-9 item 9 positive, acute flag","firstVisit":false,"me |
| S7 doctor diagnosis write + propagation | high-risk list shows diagnosis | PASS | "E2E诊断 2026-10-30T07:01:05.462Z" |
| S8 duplicate id-card regression (no 500) | S2023009 submit with shared id-card -> 200 | PASS | got 200 |
| S8 duplicate id-card regression (no 500) | S2023010 submit with shared id-card -> 200 | PASS | got 200 |
| S9 concurrency smoke (8 parallel sessions) | 8 parallel login+detail+draft all 200 | PASS | true,true,true,true,true,true,true,true |
| S10 HC notifications after high-risk submit | notifications 200 | PASS | got 200 |
| S10 HC notifications after high-risk submit | high-risk alert notification present | PASS |  |