If you need to update `analytics.v_aml_risk` or `analytics.v_aml_risk_metadata` use `data-administration`

`ScheduledRiskLevelCheckJob` has two runs, and `aml-service.enabled` (`AML_SERVICE_ENABLED`, off
unless set) decides what each does:

- **01:00 Tallinn.** Off: the views' flow, as it always was. It refreshes each family's views
  (residency, then `v_aml_risk_metadata`; `v_tkf_risk_metadata`), then writes every high-risk row
  and a `Math.random()` sample of the medium-risk ones. On: it only refreshes those three views,
  which aml-service's 02:45 shadow run reads, and writes nothing.
- **05:30 Tallinn**, after aml-service's nightly batch runs. Off: nothing. On: it takes each
  family's risk levels from aml-service (`GET /v1/risk-levels/third-pillar` and `/tkf`) and refuses a
  run not of its own Tallinn date. A family whose fetch fails (a refused run, an HTTP error, a
  timeout, a missing token or value) falls back in the same run to the views' flow, refreshing its
  views before reading them as the 01:00 flow does, and says so in the AML Slack channel. A failure
  while writing is reported as failed and does not fall back, so no second, independently sampled
  set is written.

`ConfiguredRiskLevels.isHighRisk`, which the redemption verification job asks before a payout,
follows `aml-service.high-risk-enabled` (`AML_SERVICE_HIGH_RISK_ENABLED`, off unless set). Off: the
newest `RISK_LEVEL`/`RISK_LEVEL_OVERRIDE` and `TKF_RISK_LEVEL`/`TKF_RISK_LEVEL_OVERRIDE` rows in
`aml_check` (`AmlCheckRiskLevels`). On: aml-service's `GET
/v1/risk-levels/effective/{personalCode}`, with the specialist's overrides and the related-persons
chain applied; the person is high risk when any family's `effective` level is `HIGH`, even if
another family's answer is unusable, and a person with no level in any family is not. The call has
its own short timeouts (`high-risk-connect-timeout` 1s, `high-risk-read-timeout` 2s), no retry of
its own, and a plain `RestClient.builder()` rather than the application's, so no customizer
(Sentry's span and breadcrumb interceptor, the observation one) sees the URL with the personal code
in it. When aml-service cannot answer it throws `RiskLevelUnavailableException`, whose message
carries a reason without the personal code. A failure that concerns only this person's answer (a
400, which aml-service returns only for a malformed personal code, or a parsed answer with a missing
or unknown value) stops there. Anything else (a timeout or I/O error, a body that stalls or cannot
be read, a 5xx, a 3xx, any other 4xx) means aml-service itself is failing, and it then throws for
the next 30 seconds without asking, so a hanging aml-service costs at most one timeout every 30
seconds rather than one per person. The verification job retries an unavailable risk level every
five minutes until the screening deadline, then holds the payout with `RISK_LEVEL_UNAVAILABLE`,
which the AML channel hears about with the request id. A PEP hit is held at once with `PEP` and
`RISK_LEVEL_UNAVAILABLE`, since its payout is held whatever aml-service would say. To roll back,
unset `AML_SERVICE_HIGH_RISK_ENABLED` rather than deploying an older build: once a payout is held
with `RISK_LEVEL_UNAVAILABLE`, a build without that hold reason cannot load the redemption.

`ConfiguredRiskLevels.isHighRiskCompany` asks the same of a company's payout, onboarded or not,
through `GET /v1/risk-levels/effective/companies/{registryCode}`; a company whose KYB re-screening
is unavailable retries and is held with `SCREENING_UNAVAILABLE` without its risk level, as a person
is. A company is not asked during a quiet period a person's failure started, but its own failures
quiet no one: a failure on the company endpoint alone (one company's data, or an aml-service
without the endpoint) would otherwise hold back every person after it, and the few companies cost
at most a timeout each. Unlike a person's, a company's answer with no level is unavailable rather
than not high risk: aml-service's nightly run decides every company, so an empty answer means it
has never been decided, such as one onboarded since the last run. With the flag off no company is
high risk, as there is no company level without aml-service, so the rollback above also switches
off the company hold.

A person's KYC onboarding risk is assessed while they submit their KYC survey.
`aml-service.enabled`, the nightly job's flag, chooses how: on, `AmlServiceKycChecker` asks
aml-service's `POST /v1/kyc-onboarding/assessments`; off (staging, which does not set it),
`KycCheckRepository` runs the SQL `kyc_ob_assess_user_risk`. Neither falls back to the other.
aml-service reads in its own transaction, so `KycSurveyService` commits the survey and the
sanction/PEP screenings `BeforeKycCheckedEvent` writes (the subject's, and a minor's guardians')
before asking, and `AmlServiceKycChecker` refuses to ask inside a transaction; the SQL runs after
that commit too, outside a transaction. The result is published only if the survey is still the
person's latest (`created_time`, then `id`, as aml-service orders them), so an older submission's
late answer does not overwrite a newer one's; without a lock, both are still written if the older
one's publishing stalls through the newer one's whole call. The aml-service call has its own
timeouts (`kyc-onboarding-connect-timeout` 1s, `kyc-onboarding-read-timeout` 3s) and the 5xx/I/O
retry of the nightly fetch, so a hanging aml-service adds at most about 8.5 seconds to the
submission. Open-in-view holds the request's connection through the call, so with the screening
before it a slow submission can still exceed Hikari's 10 s leak-detection threshold (a warning). A
failure fails the submission, with the survey and screenings already committed and no `KYC_CHECK`
row; a 404 (no such user or no KYC survey) is not retried.

`ScheduledAmlRiskMetadataRefreshJob` refreshes the views at 12:00 and 16:00 for Metabase.
