package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_FRESHNESS;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.BLACKROCK_ADJUSTMENT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The BlackRock adjustment is a direct input to the fee base but is entered by hand, per fund per
// day, with no idempotency key. A stale balance is indistinguishable from a correct one, so age is
// the only signal available until the entry is ingested rather than typed.
@Component
class BlackrockAdjustmentFreshnessChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final NavLedgerRepository navLedgerRepository;
  private final int maxAgeDays;

  BlackrockAdjustmentFreshnessChecker(
      NavLedgerRepository navLedgerRepository,
      @Value("${investment.fee-check.blackrock-adjustment-max-age-days:5}") int maxAgeDays) {
    this.navLedgerRepository = navLedgerRepository;
    this.maxAgeDays = maxAgeDays;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    var latest =
        navLedgerRepository.findLatestTransactionDateByType(
            BLACKROCK_ADJUSTMENT.getAccountName(fund), ADJUSTMENT);

    if (latest.isEmpty()) {
      return List.of(
          finding(
              fund,
              FeeCheckSeverity.NOT_RUN,
              "No BlackRock adjustment has ever been posted for this fund",
              Map.of()));
    }

    var latestDate = latest.get().atZone(ESTONIAN_ZONE).toLocalDate();
    var ageDays = ChronoUnit.DAYS.between(latestDate, checkDate);
    if (ageDays > maxAgeDays) {
      return List.of(
          finding(
              fund,
              FeeCheckSeverity.WARNING,
              "BlackRock adjustment is "
                  + ageDays
                  + " days old (last posted "
                  + latestDate
                  + "); the fee base may be running on a stale balance",
              Map.of("lastAdjustmentDate", latestDate.toString(), "ageDays", ageDays)));
    }
    return List.of(
        finding(
            fund,
            FeeCheckSeverity.PASS,
            "",
            Map.of("lastAdjustmentDate", latestDate.toString(), "ageDays", ageDays)));
  }

  private FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, String message, Map<String, Object> details) {
    return new FeeCheckFinding(
        fund, BLACKROCK_ADJUSTMENT_FRESHNESS, ALL, severity, message, null, details);
  }
}
