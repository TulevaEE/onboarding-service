package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

record EarlierImports(Map<TulevaFund, Instant> writtenAt) {

  private static final DateTimeFormatter WRITE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Tallinn"));

  String whatBlockingLeft(Set<TulevaFund> blockedFunds) {
    var notImported = new ArrayList<String>();
    var stillInUse = new ArrayList<String>();
    for (var fund : blockedFunds) {
      var fundWrittenAt = writtenAt.get(fund);
      if (fundWrittenAt == null) {
        notImported.add(fund.getCode());
      } else {
        stillInUse.add(
            "%s positions from %s".formatted(fund.getCode(), WRITE_TIME.format(fundWrittenAt)));
      }
    }
    var parts = new ArrayList<String>();
    if (!notImported.isEmpty()) {
      parts.add(String.join(", ", notImported) + " not imported");
    }
    if (!stillInUse.isEmpty()) {
      parts.add(String.join(", ", stillInUse) + " stay in use for NAV");
    }
    return String.join("; ", parts);
  }

  String importStatus(HealthCheckResult result) {
    if (result.hasFails() && writtenAt.containsKey(result.fund())) {
      return "blocked (earlier positions in use)";
    }
    return result.importStatus();
  }
}
