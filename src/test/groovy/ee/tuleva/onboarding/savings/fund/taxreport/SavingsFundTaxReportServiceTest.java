package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.FIFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import ee.tuleva.onboarding.savings.fund.TransactionsWithCounterparties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundTaxReportServiceTest {

  private static final String INVESTMENT_IBAN = "EE651010220306497226";
  private static final String ORDINARY_IBAN = "EE241010220306719221";

  @Mock private SavingsFundTransactionService savingsFundTransactionService;
  @Mock private InvestmentAccountService investmentAccountService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonNonMember().build();

  private SavingsFundTaxReportService savingsFundTaxReportService;

  @BeforeEach
  void setUp() {
    savingsFundTaxReportService =
        new SavingsFundTaxReportService(
            savingsFundTransactionService,
            new SavingsFundCostBasisCalculator(),
            investmentAccountService);
  }

  private static Transaction bought(String time, String units, String amount) {
    return transaction(time, units, amount, CONTRIBUTION_CASH);
  }

  private static Transaction sold(String time, String units, String amount) {
    return transaction(time, units, amount, SUBTRACTION);
  }

  private static Transaction transaction(
      String time, String units, String amount, CashFlow.Type type) {
    Instant at = Instant.parse(time);
    return Transaction.builder()
        .id(UUID.randomUUID())
        .amount(new BigDecimal(amount))
        .currency(EUR)
        .time(at)
        .priceTime(at)
        .settledTime(at)
        .isin("EE0000003283")
        .type(type)
        .units(new BigDecimal(units))
        .nav(new BigDecimal("1.00"))
        .build();
  }

  private static final class Statement {

    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<UUID, String> counterpartyIbans = new HashMap<>();

    Statement facing(Transaction transaction, String iban) {
      transactions.add(transaction);
      counterpartyIbans.put(transaction.id(), iban);
      return this;
    }

    Statement fromAnUnknownAccount(Transaction transaction) {
      transactions.add(transaction);
      return this;
    }

    TransactionsWithCounterparties build() {
      return new TransactionsWithCounterparties(
          List.copyOf(transactions), Map.copyOf(counterpartyIbans));
    }
  }

  @Test
  void reportsOnePoolWithoutLookingUpCounterpartiesWhenNoInvestmentAccountWasDeclared() {
    given(savingsFundTransactionService.getTransactions(person))
        .willReturn(
            List.of(
                bought("2025-01-10T10:00:00Z", "100", "100.00"),
                sold("2025-06-10T10:00:00Z", "100", "150.00")));
    given(investmentAccountService.declaredIban(person.getRoleCode())).willReturn(Optional.empty());

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.investmentAccount()).isNull();
    verify(savingsFundTransactionService, never()).getTransactionsWithCounterpartyIbans(person);
  }

  @Test
  void keepsInvestmentAccountGainsOutOfTheGainsSomeoneDeclares() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-01-10T10:00:00Z", "100", "100.00"), ORDINARY_IBAN)
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2025-06-10T10:00:00Z", "100", "150.00"), ORDINARY_IBAN)
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.redemptions()).hasSize(1);
    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.investmentAccount().totalGain()).isNotNull();
  }

  @Test
  void leavesAClosedYearAloneWhenALaterRedemptionGoesElsewhere() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .facing(bought("2026-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2026-07-10T10:00:00Z", "100", "300.00"), ORDINARY_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isNotNull();
    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.totalGain()).isEqualByComparingTo("0.00");
  }

  @Test
  void leavesAClosedYearAloneWhenALaterTransactionFacesAnUnknownAccount() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-01-10T10:00:00Z", "100", "100.00"), ORDINARY_IBAN)
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2025-06-10T10:00:00Z", "100", "150.00"), ORDINARY_IBAN)
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .fromAnUnknownAccount(bought("2026-03-10T10:00:00Z", "100", "300.00"))
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isNotNull();
    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.redemptions()).hasSize(1);
  }

  @Test
  void doesNotCallATransactionOrdinaryJustBecauseItsAccountIsUnknown() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-01-10T10:00:00Z", "100", "100.00"), ORDINARY_IBAN)
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .fromAnUnknownAccount(sold("2025-06-10T10:00:00Z", "100", "150.00"))
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isNull();
    assertThat(report.totalGain()).isEqualByComparingTo("110.00");
  }

  @Test
  void doesNotCallATransactionOrdinaryJustBecauseItsAccountIsGarbled() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-01-10T10:00:00Z", "100", "100.00"), ORDINARY_IBAN)
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2025-06-10T10:00:00Z", "100", "150.00"), "NOT_AN_IBAN")
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isNull();
    assertThat(report.totalGain()).isEqualByComparingTo("110.00");
  }

  @Test
  void recognisesTheDeclaredAccountHoweverTheBankSpacedIt() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), "ee65 1010 2203 0649 7226")
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isNotNull();
    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.totalGain()).isEqualByComparingTo("0.00");
  }

  @Test
  void splitsThePoolsWhenAPurchaseAndARedemptionShareTheSameInstant() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(sold("2025-06-10T10:00:00Z", "100", "150.00"), ORDINARY_IBAN)
                .facing(sold("2025-02-10T10:00:00Z", "100", "260.00"), INVESTMENT_IBAN)
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(bought("2025-01-10T10:00:00Z", "100", "100.00"), ORDINARY_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.redemptions()).hasSize(1);
  }

  @Test
  void doesNotPickAPoolForSomeoneWhoRedeemedToAnotherAccountThanTheyBoughtFrom() {
    given(savingsFundTransactionService.getTransactionsWithCounterpartyIbans(person))
        .willReturn(
            new Statement()
                .facing(bought("2025-02-10T10:00:00Z", "100", "200.00"), INVESTMENT_IBAN)
                .facing(sold("2025-07-10T10:00:00Z", "100", "260.00"), ORDINARY_IBAN)
                .build());
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.investmentAccount().totalGain()).isNull();
  }
}
