package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SettlementFromBankListener {

  private final Clock clock;
  private final SettlementFromBankService settlementFromBankService;

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @EventListener
  @Order(SebPriceVsNavCheckListener.LISTENER_ORDER + 10)
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() == SEB && event.reportType() == PENDING_TRANSACTIONS) {
      settleTheLookback();
    }
  }

  private void settleTheLookback() {
    LocalDate since = LocalDate.now(clock).minusDays(scanLookbackDays);
    try {
      settlementFromBankService.settleOrdersBookedSince(since);
    } catch (RuntimeException e) {
      log.error("Settling registry orders from the bank failed: since={}", since, e);
    }
  }
}
