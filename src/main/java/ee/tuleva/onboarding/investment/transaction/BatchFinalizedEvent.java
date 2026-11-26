package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record BatchFinalizedEvent(
    List<Long> batchIds, int orderCount, LocalDate tradeDate, Map<String, String> driveFileUrls) {}
