package ee.tuleva.onboarding.aml;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum ScreeningBatch {
  THIRD_PILLAR("third-pillar", "batch"),
  SAVINGS_FUND("savings fund", "savings-fund-batch");

  final String population;
  final String metricPhase;
}
