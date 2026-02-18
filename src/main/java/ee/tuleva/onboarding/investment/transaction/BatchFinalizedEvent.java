package ee.tuleva.onboarding.investment.transaction;

import java.util.Map;

record BatchFinalizedEvent(
    Long batchId, int orderCount, String tradeDate, Map<String, String> driveFileUrls) {}
