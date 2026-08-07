package ee.tuleva.onboarding.investment.check.fee;

// A send that failed is not the same as having had nothing to say. Only the first must stop the
// run from becoming the baseline the next run diffs against, or the alert is lost for good.
enum FeeCheckNotification {
  NOTHING_TO_REPORT,
  SENT,
  SEND_FAILED
}
