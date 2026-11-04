package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankListenerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneId.of("Europe/Tallinn"));
  private static final LocalDate SIXTY_DAYS_AGO = LocalDate.of(2026, 9, 4);

  @Mock private SettlementFromBankService settlementFromBankService;

  private SettlementFromBankListener listener() {
    return new SettlementFromBankListener(CLOCK, settlementFromBankService);
  }

  @Test
  void sebPendingReportImported_settlesTheOrdersOfTheLookback() {
    listener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 11, 3), 1));

    then(settlementFromBankService).should().settleOrdersBookedSince(SIXTY_DAYS_AGO);
  }

  @Test
  void otherReportsImported_settleNothing() {
    listener()
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 11, 3), 1));
    listener()
        .onReportImportCompleted(
            new ReportImportCompleted(
                SWEDBANK, PENDING_TRANSACTIONS, LocalDate.of(2026, 11, 3), 1));

    then(settlementFromBankService).shouldHaveNoInteractions();
  }

  @Test
  void aFailureToSettle_doesNotFailTheReportImport() {
    willThrow(new IllegalStateException("boom"))
        .given(settlementFromBankService)
        .settleOrdersBookedSince(any());

    assertThatCode(
            () ->
                listener()
                    .onReportImportCompleted(
                        new ReportImportCompleted(
                            SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 11, 3), 1)))
        .doesNotThrowAnyException();
  }
}
