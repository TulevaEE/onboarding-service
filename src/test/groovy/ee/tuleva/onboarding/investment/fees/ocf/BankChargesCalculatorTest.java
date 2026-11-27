package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.BANK_CHARGES_NOT_IN_LEDGER;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.BANK_CHARGES_WITHOUT_AVERAGE_AUM;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.TransactionCost;
import ee.tuleva.onboarding.ledger.BankCharges;
import ee.tuleva.onboarding.ledger.BankChargesRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankChargesCalculatorTest {

  private static final LocalDate MONTH_END = LocalDate.of(2026, 11, 30);
  private static final LocalDate TRAILING_YEAR_START = LocalDate.of(2025, 12, 1);
  private static final LocalDate SEB_DEPOT_START = LocalDate.of(2026, 3, 1);
  private static final BigDecimal TRANSACTION_WINDOW_AVERAGE_AUM = new BigDecimal("1000000");

  @Mock private BankChargesRepository bankChargesRepository;
  @Mock private FundNavQueryService fundNavQueryService;

  @InjectMocks private BankChargesCalculator calculator;

  @Test
  void theChargesOfAWholeTrailingYearAreTakenAtTheirObservedRateOnTheTransactionWindowsAum() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(LocalDate.of(2025, 6, 1)));
    given(bankChargesRepository.chargedBetween(TUK75, TRAILING_YEAR_START, MONTH_END))
        .willReturn(new BankCharges(new BigDecimal("100"), new BigDecimal("20")));

    var cost =
        calculator.costOver(TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of()));

    assertThat(cost)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new BankChargesCost(
                new BigDecimal("0.00012"),
                TRAILING_YEAR_START,
                new BankCharges(new BigDecimal("100"), new BigDecimal("20")),
                List.of()));
    verifyNoInteractions(fundNavQueryService);
  }

  @Test
  void aLedgerThatStartsInsideTheWindowIsAnnualisedOverTheDaysItCoversOnTheirOwnAverageAum() {
    var navDates =
        List.of(
            TRAILING_YEAR_START, LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 2), MONTH_END);
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(SEB_DEPOT_START));
    given(bankChargesRepository.chargedBetween(TUK75, SEB_DEPOT_START, MONTH_END))
        .willReturn(new BankCharges(new BigDecimal("55"), ZERO));
    given(fundNavQueryService.findAum(TUK75.getCode(), LocalDate.of(2026, 3, 2)))
        .willReturn(new BigDecimal("900000"));
    given(fundNavQueryService.findAum(TUK75.getCode(), MONTH_END))
        .willReturn(new BigDecimal("1100000"));

    var cost =
        calculator.costOver(TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, navDates));

    assertThat(cost)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new BankChargesCost(
                new BigDecimal("0.000073"),
                SEB_DEPOT_START,
                new BankCharges(new BigDecimal("55"), ZERO),
                List.of()));
  }

  @Test
  void withoutAnyOfTheFundsBankStatementsInTheLedgerItsBankChargesAreUnknown() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75)).willReturn(Optional.empty());

    var cost =
        calculator.costOver(TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of()));

    assertThat(cost)
        .isEqualTo(new BankChargesCost(ZERO, null, null, List.of(BANK_CHARGES_NOT_IN_LEDGER)));
  }

  @Test
  void aWindowThatEndsBeforeTheLedgersFirstStatementHasUnknownBankCharges() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(MONTH_END.plusDays(1)));

    var cost =
        calculator.costOver(TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of()));

    assertThat(cost)
        .isEqualTo(new BankChargesCost(ZERO, null, null, List.of(BANK_CHARGES_NOT_IN_LEDGER)));
    then(bankChargesRepository).should().statementsInLedgerFrom(TUK75);
    then(bankChargesRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void aMonthThatEndsBeforeTheFundExistedHasNoBankCharges() {
    var inception = TKF100.getInceptionDate();
    var monthEndBeforeInception = inception.minusDays(2);

    var cost =
        calculator.costOver(
            TKF100, transactionWindow(inception, monthEndBeforeInception, List.of()));

    assertThat(cost)
        .isEqualTo(new BankChargesCost(ZERO, inception, new BankCharges(ZERO, ZERO), List.of()));
    verifyNoInteractions(bankChargesRepository, fundNavQueryService);
  }

  @Test
  void aWindowWithoutChargesHasAZeroRateEvenWithoutAnAverageAum() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(TRAILING_YEAR_START));
    given(bankChargesRepository.chargedBetween(TUK75, TRAILING_YEAR_START, MONTH_END))
        .willReturn(new BankCharges(ZERO, ZERO));

    var cost =
        calculator.costOver(
            TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of(), ZERO));

    assertThat(cost)
        .isEqualTo(
            new BankChargesCost(ZERO, TRAILING_YEAR_START, new BankCharges(ZERO, ZERO), List.of()));
  }

  @Test
  void chargesWithoutAnAverageAumToDivideByLeaveTheRateUnknownUntilANavIsPublished() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(TRAILING_YEAR_START));
    var charges = new BankCharges(new BigDecimal("10"), ZERO);
    given(bankChargesRepository.chargedBetween(TUK75, TRAILING_YEAR_START, MONTH_END))
        .willReturn(charges);

    var cost =
        calculator.costOver(
            TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of(), ZERO));

    assertThat(cost)
        .isEqualTo(
            new BankChargesCost(
                ZERO, TRAILING_YEAR_START, charges, List.of(BANK_CHARGES_WITHOUT_AVERAGE_AUM)));
  }

  @Test
  void aLedgerStartingInsideTheWindowWithNoPublishedNavSinceLeavesTheRateUnknown() {
    given(bankChargesRepository.statementsInLedgerFrom(TUK75))
        .willReturn(Optional.of(SEB_DEPOT_START));
    var charges = new BankCharges(new BigDecimal("10"), ZERO);
    given(bankChargesRepository.chargedBetween(TUK75, SEB_DEPOT_START, MONTH_END))
        .willReturn(charges);

    var cost =
        calculator.costOver(
            TUK75, transactionWindow(TRAILING_YEAR_START, MONTH_END, List.of(TRAILING_YEAR_START)));

    assertThat(cost)
        .isEqualTo(
            new BankChargesCost(
                ZERO, SEB_DEPOT_START, charges, List.of(BANK_CHARGES_WITHOUT_AVERAGE_AUM)));
    then(fundNavQueryService).should(never()).findAum(any(), any());
  }

  private static TransactionCost transactionWindow(
      LocalDate start, LocalDate end, List<LocalDate> navDates) {
    return transactionWindow(start, end, navDates, TRANSACTION_WINDOW_AVERAGE_AUM);
  }

  private static TransactionCost transactionWindow(
      LocalDate start, LocalDate end, List<LocalDate> navDates, BigDecimal averageAum) {
    return new TransactionCost(ZERO, start, end, ZERO, averageAum, navDates, List.of());
  }
}
