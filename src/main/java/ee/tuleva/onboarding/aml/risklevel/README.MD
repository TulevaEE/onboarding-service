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

`ScheduledAmlRiskMetadataRefreshJob` refreshes the views at 12:00 and 16:00 for Metabase.
