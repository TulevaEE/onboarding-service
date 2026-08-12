package ee.tuleva.onboarding.investment.check.fee;

// A send that failed is not the same as having had nothing to say: only the first may stop the run
// becoming the baseline the next run diffs against.
enum FeeCheckNotification {
  NOTHING_TO_REPORT,
  SENT,
  SEND_FAILED
}
