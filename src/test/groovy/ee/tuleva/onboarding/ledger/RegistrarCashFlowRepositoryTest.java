package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_PAYOUT;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.FUND_PENSION;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.ONE_OFF_WITHDRAWAL;
import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.UNRECOGNISED;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.FundBankLedger.UnclassifiedEntryDetails;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  LedgerAccountService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  FundBankLedger.class,
  RegistrarCashFlowRepository.class,
  ClockConfig.class
})
class RegistrarCashFlowRepositoryTest {

  private static final LocalDate JUNE_FIRST = LocalDate.of(2026, 6, 1);
  private static final LocalDate JUNE_LAST = LocalDate.of(2026, 6, 30);
  private static final String RECURRING = "Fondipensioni maksete lunastamine";
  private static final String ONE_OFF = "Ühekordsete maksete osakute lunastamine";

  private static final RecursiveComparisonConfiguration AMOUNTS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();

  @Autowired FundBankLedger fundBankLedger;
  @Autowired RegistrarCashFlowRepository repository;

  @Test
  void readsContributionsAsTheCashTheFundReceivedOnEachBookingDateInsideTheWindow() {
    contribution(TUK75, "900.00", LocalDate.of(2026, 5, 29));
    contribution(TUK75, "1200.50", JUNE_FIRST);
    contribution(TUK75, "300.00", JUNE_LAST);
    contribution(TUK75, "700.00", LocalDate.of(2026, 7, 1));
    contribution(TUK00, "400.00", LocalDate.of(2026, 6, 15));
    payout(TUK75, "50.00", LocalDate.of(2026, 6, 15), RECURRING);

    assertThat(repository.findContributions(TUK75, JUNE_FIRST, JUNE_LAST))
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new RegistrarContribution(JUNE_FIRST, new BigDecimal("1200.50")),
            new RegistrarContribution(JUNE_LAST, new BigDecimal("300.00")));
  }

  @Test
  void readsPayoutsAsTheCashTheFundPaidWithTheReasonTheRegistrarGave() {
    payout(TUK75, "250.00", LocalDate.of(2026, 6, 10), RECURRING);
    payout(TUK75, "80.00", LocalDate.of(2026, 6, 11), "Synthetic payout reason nobody mapped");
    payout(TUK75, "90.00", LocalDate.of(2026, 7, 2), ONE_OFF);
    payout(TUK00, "60.00", LocalDate.of(2026, 6, 12), ONE_OFF);
    contribution(TUK75, "1000.00", LocalDate.of(2026, 6, 10));

    assertThat(repository.findPayouts(TUK75, JUNE_FIRST, JUNE_LAST))
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new RegistrarPayout(LocalDate.of(2026, 6, 10), new BigDecimal("250.00"), FUND_PENSION),
            new RegistrarPayout(LocalDate.of(2026, 6, 11), new BigDecimal("80.00"), UNRECOGNISED));
  }

  @Test
  void aPayoutReclassifiedFromSuspenseKeepsTheReasonOfItsOriginalBankEntry() {
    var externalReference = randomUUID();
    var bookingDate = LocalDate.of(2026, 6, 18);
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("-420.00"),
        externalReference,
        FUND_INVESTMENT_CASH_CLEARING,
        bookingDate,
        new UnclassifiedEntryDetails(null, null, ONE_OFF, null));
    fundBankLedger.reclassifySuspenseEntry(
        TUK75, new BigDecimal("-420.00"), externalReference, REGISTRAR_PAYOUT, bookingDate);

    assertThat(repository.findPayouts(TUK75, JUNE_FIRST, JUNE_LAST))
        .usingRecursiveFieldByFieldElementComparator(AMOUNTS_BY_VALUE)
        .containsExactly(
            new RegistrarPayout(bookingDate, new BigDecimal("420.00"), ONE_OFF_WITHDRAWAL));
  }

  @Test
  void theFirstCashBookingDateIsTheOpeningBalanceTheLedgerWasSeededFrom() {
    fundBankLedger.recordOpeningBalance(TUK75, new BigDecimal("5000.00"), JUNE_FIRST);
    contribution(TUK75, "100.00", LocalDate.of(2026, 6, 3));
    contribution(TUK00, "100.00", LocalDate.of(2026, 5, 4));

    assertThat(repository.findFirstCashBookingDate(TUK75)).contains(JUNE_FIRST);
  }

  @Test
  void aFundWhoseCashTheLedgerHasNeverSeenHasNoFirstBookingDate() {
    contribution(TUK00, "100.00", LocalDate.of(2026, 5, 4));

    assertThat(repository.findFirstCashBookingDate(TUK75)).isEmpty();
  }

  private void contribution(TulevaFund fund, String amount, LocalDate bookingDate) {
    fundBankLedger.recordRegistrarContribution(
        fund,
        new BigDecimal(amount),
        randomUUID(),
        bookingDate,
        "Synthetic registrar contribution");
  }

  private void payout(TulevaFund fund, String amount, LocalDate bookingDate, String remittance) {
    fundBankLedger.recordRegistrarPayout(
        fund, new BigDecimal(amount).negate(), randomUUID(), bookingDate, remittance);
  }
}
