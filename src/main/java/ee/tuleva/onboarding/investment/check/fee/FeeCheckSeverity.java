package ee.tuleva.onboarding.investment.check.fee;

// Ordered by how loudly a run needs answering: the notifier and the event row both take the highest
// severity a check produced, so INFO must rank below NOT_RUN - a difference we have already
// explained matters less than a day we could not look at.
enum FeeCheckSeverity {
  PASS,
  INFO,
  NOT_RUN,
  WARNING,
  FAIL
}
