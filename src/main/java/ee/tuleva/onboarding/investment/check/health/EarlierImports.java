package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

record EarlierImports(Map<TulevaFund, Instant> importedAt) {

  private static final DateTimeFormatter IMPORT_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Tallinn"));

  String whatBlockingLeft(Set<TulevaFund> blockedFunds) {
    var notImported = new ArrayList<String>();
    var stillInUse = new ArrayList<String>();
    for (var fund : blockedFunds) {
      var fundImportedAt = importedAt.get(fund);
      if (fundImportedAt == null) {
        notImported.add(fund.getCode());
      } else {
        stillInUse.add(
            "%s positions written %s"
                .formatted(fund.getCode(), IMPORT_TIME.format(fundImportedAt)));
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
    if (result.hasFails() && importedAt.containsKey(result.fund())) {
      return "blocked (earlier positions in use)";
    }
    return result.importStatus();
  }
}
