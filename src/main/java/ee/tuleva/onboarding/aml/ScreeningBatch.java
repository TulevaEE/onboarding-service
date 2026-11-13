package ee.tuleva.onboarding.aml;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum ScreeningBatch {
  THIRD_PILLAR("third-pillar", "batch"),
  SAVINGS_FUND("savings fund", "savings-fund-batch"),
  SAVINGS_FUND_GUARDIANS("savings fund guardian", "savings-fund-guardian-batch");

  final String population;
  final String metricPhase;
}
