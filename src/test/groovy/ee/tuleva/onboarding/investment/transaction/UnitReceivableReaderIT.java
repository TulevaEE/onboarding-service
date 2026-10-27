package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({UnitReceivableReaderIT.LedgerWriting.class, UnitReceivableReader.class, ClockConfig.class})
class UnitReceivableReaderIT {

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.ledger",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern =
                  "ee\\.tuleva\\.onboarding\\.ledger\\.(FundBankLedger|LedgerAccountService"
                      + "|LedgerTransactionService|UserUnitBalanceGuard"
                      + "|RegistrarCashFlowRepository)"))
  static class LedgerWriting {}

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 6, 10);
  private static final LocalDate TRADE_DAY = LocalDate.of(2026, 6, 11);

  private static final RecursiveComparisonConfiguration AMOUNTS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();

  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired UnitReceivableReader reader;

  @Test
  void readsTheCustodiansUnitReceivableAndTheRegistrarReceiptsBookedAfterTheReportDate() {
    reportRow(TUK75, "Receivables of outstanding units", TUK75.getIsin(), "8000.00");
    reportRow(TUK75, "Total receivables of unsettled transactions", TUK75.getIsin(), "20000.00");
    reportRow(TUK00, "Receivables of outstanding units", TUK00.getIsin(), "600.00");
    registrarReceipt(TUK75, "1500.00", REPORT_DATE);
    registrarReceipt(TUK75, "5000.00", TRADE_DAY);
    registrarReceipt(TUK75, "900.00", TRADE_DAY.plusDays(1));
    registrarReceipt(TUK00, "600.00", TRADE_DAY);

    var unitReceivable = reader.read(TUK75, REPORT_DATE, TRADE_DAY);

    assertThat(unitReceivable)
        .usingRecursiveComparison(AMOUNTS_BY_VALUE)
        .isEqualTo(new UnitReceivable(new BigDecimal("8000.00"), new BigDecimal("5000.00"), false));
  }

  @Test
  void theThirdPillarFundMayCountTheReceivableBeforeItArrives() {
    reportRow(TUV100, "Receivables of outstanding units", TUV100.getIsin(), "4000.00");

    var unitReceivable = reader.read(TUV100, REPORT_DATE, TRADE_DAY);

    assertThat(unitReceivable.countedBeforeItArrives()).isTrue();
  }

  @Test
  void leavesTheSavingsFundOutBecauseItsSubscriptionsReachTheFundThroughItsOwnRegister() {
    reportRow(TKF100, "Receivables of outstanding units", TKF100.getIsin(), "3000.00");

    var unitReceivable = reader.read(TKF100, REPORT_DATE, TRADE_DAY);

    assertThat(unitReceivable).isEqualTo(UnitReceivable.NONE);
  }

  private void reportRow(TulevaFund fund, String accountName, String accountId, String value) {
    fundPositionRepository.save(
        FundPosition.builder()
            .fund(fund)
            .navDate(REPORT_DATE)
            .accountType(RECEIVABLES)
            .accountName(accountName)
            .accountId(accountId)
            .marketValue(new BigDecimal(value))
            .build());
  }

  private void registrarReceipt(TulevaFund fund, String amount, LocalDate bookingDate) {
    fundBankLedger.recordRegistrarContribution(
        fund, new BigDecimal(amount), randomUUID(), bookingDate, "Synthetic registrar receipt");
  }
}
