package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class NavReconciliationService {

  static final BigDecimal AUM_TOLERANCE_PERCENT = new BigDecimal("0.10");

  private final NavReportRepository navReportRepository;
  private final FundValueProvider fundValueProvider;
  private final OperationsNotificationService notificationService;

  void reconcile(LocalDate navDate) {
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund.hasNavCalculation()) {
        reconcileFund(fund, navDate);
      }
    }
  }

  private void reconcileFund(TulevaFund fund, LocalDate navDate) {
    List<NavReportRow> rows =
        navReportRepository.findLatestByNavDateAndFundCode(navDate, fund.getCode());
    if (rows.isEmpty()) {
      log.info("No nav_report rows for reconciliation: fund={}, date={}", fund.getCode(), navDate);
      return;
    }

    Optional<NavReportRow> navRow =
        rows.stream().filter(r -> "NAV".equals(r.getAccountType())).findFirst();
    Optional<NavReportRow> unitsRow =
        rows.stream().filter(r -> "UNITS".equals(r.getAccountType())).findFirst();

    List<String> mismatches = new ArrayList<>();

    navRow.ifPresent(
        row -> {
          BigDecimal reportNav = row.getMarketPrice();
          Optional<FundValue> fundValue =
              fundValueProvider.getValueForDate(fund.getIsin(), navDate);
          if (fundValue.isEmpty()) {
            mismatches.add("NAV fund_value missing (nav_report has " + reportNav + ")");
          } else if (reportNav.compareTo(fundValue.get().value()) != 0) {
            mismatches.add(
                "NAV mismatch: nav_report="
                    + reportNav
                    + ", fund_value="
                    + fundValue.get().value());
          }
        });

    unitsRow.ifPresent(
        row -> {
          BigDecimal reportAum = row.getMarketValue();
          Optional<FundValue> fundValue =
              fundValueProvider.getValueForDate(fund.getAumKey(), navDate);
          if (fundValue.isEmpty()) {
            mismatches.add("AUM fund_value missing (nav_report has " + reportAum + ")");
          } else if (exceedsTolerance(reportAum, fundValue.get().value())) {
            mismatches.add(
                "AUM mismatch: nav_report="
                    + reportAum
                    + ", fund_value="
                    + fundValue.get().value());
          }
        });

    if (!mismatches.isEmpty()) {
      String message =
          "CSV-to-API reconciliation failed: fund="
              + fund.getCode()
              + ", date="
              + navDate
              + "\n"
              + String.join("\n", mismatches);
      log.warn("{}", message);
      notificationService.sendMessage(message, INVESTMENT);
    } else {
      log.info("CSV-to-API reconciliation passed: fund={}, date={}", fund.getCode(), navDate);
    }
  }

  private boolean exceedsTolerance(BigDecimal expected, BigDecimal actual) {
    if (expected.signum() == 0) {
      return actual.signum() != 0;
    }
    BigDecimal percentDiff =
        expected
            .subtract(actual)
            .abs()
            .multiply(new BigDecimal("100"))
            .divide(expected.abs(), 6, HALF_UP);
    return percentDiff.compareTo(AUM_TOLERANCE_PERCENT) > 0;
  }
}
