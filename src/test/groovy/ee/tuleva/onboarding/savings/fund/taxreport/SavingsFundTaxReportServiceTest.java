package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.savings.fund.taxreport.CostBasisMethod.FIFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundTaxReportServiceTest {

  private static final String INVESTMENT_IBAN = "EE471000001020145685";
  private static final String ORDINARY_IBAN = "EE342200221020145685";

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

  private static Transaction bought(String time, String units, String amount, String iban) {
    return transaction(time, units, amount, iban, CONTRIBUTION_CASH);
  }

  private static Transaction sold(String time, String units, String amount, String iban) {
    return transaction(time, units, amount, iban, SUBTRACTION);
  }

  private static Transaction transaction(
      String time,
      String units,
      String amount,
      String iban,
      ee.tuleva.onboarding.epis.cashflows.CashFlow.Type type) {
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
        .counterpartyIban(iban)
        .build();
  }

  @Test
  void reportsOnePoolWhenNoInvestmentAccountWasDeclared() {
    given(savingsFundTransactionService.getTransactions(person))
        .willReturn(
            List.of(
                bought("2025-01-10T10:00:00Z", "100", "100.00", ORDINARY_IBAN),
                sold("2025-06-10T10:00:00Z", "100", "150.00", ORDINARY_IBAN)));
    given(investmentAccountService.declaredIban(person.getRoleCode())).willReturn(Optional.empty());

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.investmentAccount()).isNull();
  }

  @Test
  void keepsInvestmentAccountGainsOutOfTheGainsSomeoneDeclares() {
    given(savingsFundTransactionService.getTransactions(person))
        .willReturn(
            List.of(
                bought("2025-01-10T10:00:00Z", "100", "100.00", ORDINARY_IBAN),
                bought("2025-02-10T10:00:00Z", "100", "200.00", INVESTMENT_IBAN),
                sold("2025-06-10T10:00:00Z", "100", "150.00", ORDINARY_IBAN),
                sold("2025-07-10T10:00:00Z", "100", "260.00", INVESTMENT_IBAN)));
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("50.00");
    assertThat(report.redemptions()).hasSize(1);
    assertThat(report.investmentAccount().iban()).isEqualTo(INVESTMENT_IBAN);
    assertThat(report.investmentAccount().totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.investmentAccount().redeemedOutsideTheAccount()).isFalse();
  }

  @Test
  void doesNotPickAPoolForSomeoneWhoRedeemedToAnotherAccountThanTheyBoughtFrom() {
    given(savingsFundTransactionService.getTransactions(person))
        .willReturn(
            List.of(
                bought("2025-02-10T10:00:00Z", "100", "200.00", INVESTMENT_IBAN),
                sold("2025-07-10T10:00:00Z", "100", "260.00", ORDINARY_IBAN)));
    given(investmentAccountService.declaredIban(person.getRoleCode()))
        .willReturn(Optional.of(INVESTMENT_IBAN));

    SavingsFundTaxReport report = savingsFundTaxReportService.getTaxReport(person, 2025, FIFO);

    assertThat(report.totalGain()).isEqualByComparingTo("60.00");
    assertThat(report.investmentAccount().redeemedOutsideTheAccount()).isTrue();
    assertThat(report.investmentAccount().redemptions()).isEmpty();
  }
}
