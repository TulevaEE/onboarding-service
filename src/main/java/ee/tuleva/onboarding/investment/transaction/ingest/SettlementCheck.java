package ee.tuleva.onboarding.investment.transaction.ingest;

enum SettlementCheck {
  CHECKED,
  SKIPPED_EMPTY_REPORT,
  SKIPPED_NO_MATCHED_ROW,
  SKIPPED_MALFORMED_ROWS,
  SKIPPED_NOT_LATEST_REPORT,
  SKIPPED_POSSIBLE_TRUNCATION
}
