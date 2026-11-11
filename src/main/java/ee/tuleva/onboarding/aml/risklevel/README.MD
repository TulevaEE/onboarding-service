The risk rules live in aml-service; this package asks it and records or acts on its answers. There
is no switch back to the SQL in `database-administration` that computed them before.

`ScheduledRiskLevelCheckJob` runs after aml-service's nightly batch. It takes each family's risk
levels from aml-service (`GET /v1/risk-levels/third-pillar` and `/tkf`), refuses a run not of its
own Tallinn date, and writes them as `RISK_LEVEL` and `TKF_RISK_LEVEL` checks. A family whose fetch
fails (a refused run, an HTTP error, a timeout, a missing token or value) writes nothing that night;
one whose write fails keeps the rows written before the failure. Either is reported as failed in
the AML Slack channel, and the other family still runs.

`EffectiveRiskLevels.isHighRisk`, which the redemption verification job asks before a payout, asks
aml-service's `GET /v1/risk-levels/effective/{personalCode}`, with the specialist's overrides and
the related-persons chain applied; the person is high risk when any family's `effective` level is
`HIGH`, even if another family's answer is unusable, and a person with no level in any family is
not. The call has its own short timeouts, no retry of its own, and a plain `RestClient.builder()`
rather than the application's, so no customizer (Sentry's span and breadcrumb interceptor, the
observation one) sees the URL with the personal code in it. When aml-service cannot answer it
throws `RiskLevelUnavailableException`, whose message carries a reason without the personal code.
A failure that concerns only this person's answer (a 400, which aml-service returns only for a
malformed personal code, or a parsed answer with a missing or unknown value) stops there. Anything
else (a timeout or I/O error, a body that stalls or cannot be read, a 5xx, a 3xx, any other 4xx)
means aml-service itself is failing, and it then throws without asking for
`QUIET_AFTER_AML_SERVICE_FAILED`, so a hanging aml-service costs one timeout per quiet period rather
than one per person. The verification job retries an unavailable risk level until the screening
deadline, then holds the payout with `RISK_LEVEL_UNAVAILABLE`, which the AML channel hears about
with the request id. A PEP hit is held at once with `PEP` and `RISK_LEVEL_UNAVAILABLE`, since its
payout is held whatever aml-service would say.

`EffectiveRiskLevels.isHighRiskCompany` asks the same of a company's payout, onboarded or not,
through `GET /v1/risk-levels/effective/companies/{registryCode}`; a company whose KYB re-screening
is unavailable retries and is held with `SCREENING_UNAVAILABLE` without its risk level, as a person
is. A company is not asked during a quiet period a person's failure started, but its own failures
quiet no one: a failure on the company endpoint alone (one company's data, or an aml-service
without the endpoint) would otherwise hold back every person after it, and the few companies cost
at most a timeout each. Unlike a person's, a company's answer with no level is unavailable rather
than not high risk: aml-service's nightly run decides every company, so an empty answer means it
has never been decided, such as one onboarded since the last run.

A person's KYC onboarding risk is assessed while they submit their KYC survey, by
`AmlServiceKycChecker` asking aml-service's `POST /v1/kyc-onboarding/assessments`.
aml-service reads in its own transaction, so `KycSurveyService` commits the survey and the
sanction/PEP screenings `BeforeKycCheckedEvent` writes (the subject's, and a minor's guardians')
before asking, and `AmlServiceKycChecker` refuses to ask inside a transaction. The result is
published only if the survey is still the person's latest (`created_time`, then `id`, as aml-service
orders them), so an older submission's late answer does not overwrite a newer one's; without a
lock, both are still written if the older one's publishing stalls through the newer one's whole
call. The call has its own timeouts (`aml-service.kyc-onboarding-*`) and the nightly fetch's retry
on a 5xx or I/O error, which together bound how long a hanging aml-service holds up a submission.
Open-in-view holds the request's connection through the call, so with the screening before it a
slow submission can still exceed Hikari's leak-detection threshold (a warning). A failure fails the
submission, with the survey and screenings already committed and no `KYC_CHECK` row; a 404 (no such
user or no KYC survey) is not retried.

Staging runs no aml-service, so in the `dev` and `staging` profiles nothing asks it: the nightly job
does not run, and the KYC check and the payout hold get a mock in its place. `DevKycChecker` passes
every KYC check as `LOW`, its metadata saying nothing was assessed, and `DevRiskLevels` holds no
person's or company's payout. Every other profile gets the real ones above, so a missing profile
cannot quietly give production the fail-open mock.

The `db/dev` stand-ins for the old risk views (`V1_173.1`, `V1_173.2`) are applied migrations, so
they stay, but nothing reads them.
