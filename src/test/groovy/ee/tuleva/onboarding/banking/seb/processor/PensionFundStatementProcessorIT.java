package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.UNRECOGNISED_MANAGEMENT_COMPANY_DEBIT;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckEvent;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckEventRepository;
import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry.CounterPartyDetails;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class PensionFundStatementProcessorIT {

  private static final String TUK75_IBAN = "EE001234567890123475";
  private static final BankAccount TUK75_ACCOUNT =
      new BankAccount(TUK75_IBAN, FUND_INVESTMENT_EUR, TUK75, "gw-test");
  private static final String MANAGER_NAME = "Tuleva Fondid AS";
  private static final String MANAGER_IBAN = "EE001234567890123488";
  private static final LocalDate PAYMENT_DAY = LocalDate.of(2026, 5, 12);
  private static final Instant PAYMENT_BOOKED = Instant.parse("2026-05-12T10:00:00Z");

  @Autowired private PensionFundStatementProcessor processor;
  @Autowired private LedgerService ledgerService;
  @Autowired private PaymentCheckEventRepository paymentCheckEventRepository;

  @Test
  void aPaymentToTheManagerNotStatedAsAFeeIsHeldInSuspenseWithAWarningBesideTheFeePayment() {
    processor.process(
        statementWith(
            managerDebit("fee-04", new BigDecimal("-1234.56"), "TUK75 valitsemistasu 04/2026"),
            managerDebit("other-04", new BigDecimal("-50.00"), "BR tasud TUK75")),
        TUK75_ACCOUNT);

    assertThat(balance(MANAGEMENT_FEE)).isEqualByComparingTo(new BigDecimal("1234.56"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(findings())
        .extracting(PaymentCheckEvent::getCheckType, PaymentCheckEvent::getSeverity)
        .containsExactly(tuple(UNRECOGNISED_MANAGEMENT_COMPANY_DEBIT, WARNING));
  }

  private List<PaymentCheckEvent> findings() {
    return paymentCheckEventRepository
        .findByCheckTypeAndExternalKey(
            UNRECOGNISED_MANAGEMENT_COMPANY_DEBIT, externalReferenceOf("other-04"))
        .stream()
        .toList();
  }

  private static String externalReferenceOf(String externalId) {
    return UUID.nameUUIDFromBytes((TUK75_IBAN + ":" + externalId).getBytes(UTF_8)).toString();
  }

  private BigDecimal balance(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount, TUK75).getBalance();
  }

  private static BankStatement statementWith(BankStatementEntry... entries) {
    return new BankStatement(
        BankStatementType.HISTORIC_STATEMENT,
        new BankStatementAccount(TUK75_IBAN, "Tuleva Maailma Aktsiate Pensionifond", "14118923"),
        List.of(),
        List.of(entries),
        new StatementPeriod(PAYMENT_DAY, PAYMENT_DAY));
  }

  private static BankStatementEntry managerDebit(
      String externalId, BigDecimal amount, String remittanceInformation) {
    return new BankStatementEntry(
        new CounterPartyDetails(MANAGER_NAME, MANAGER_IBAN, null),
        amount,
        "EUR",
        TransactionType.DEBIT,
        remittanceInformation,
        externalId,
        null,
        "ICDT",
        PAYMENT_BOOKED,
        true);
  }
}
