package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Map;

record BatchFinalizedEvent(
    List<Long> batchIds, int orderCount, String tradeDate, Map<String, String> driveFileUrls) {}
