package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavingFundPaymentExtractorTest {

  private final SavingFundPaymentExtractor extractor = new SavingFundPaymentExtractor();

  @Test
  void extractPayments_shouldExtractPaymentsFromDocument() {
    // given
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");

    var creditEntry =
        createCreditEntry(
            new BigDecimal("0.10"),
            "EE157700771001802057",
            "Jüri Tamm",
            "39910273027",
            "39910273027",
            "2025092900654847-1");

    var debitEntry =
        createDebitEntry(
            new BigDecimal("-0.10"),
            "EE157700771001802057",
            "Jüri Tamm",
            null,
            "bounce-back",
            "2025092900673437-1");

    var statement = createBankStatement(account, List.of(creditEntry, debitEntry));

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement);

    // then
    assertThat(payments).hasSize(2);

    // Verify first payment (credit transaction)
    SavingFundPayment firstPayment = payments.get(0);
    assertThat(firstPayment.getAmount()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(firstPayment.getCurrency()).isEqualTo(Currency.EUR);
    assertThat(firstPayment.getDescription()).isEqualTo("39910273027");
    assertThat(firstPayment.getRemitterIban()).isEqualTo("EE157700771001802057");
    assertThat(firstPayment.getRemitterIdCode()).isEqualTo("39910273027");
    assertThat(firstPayment.getBeneficiaryIban()).isEqualTo("EE442200221092874625");
    assertThat(firstPayment.getBeneficiaryIdCode()).isEqualTo("14118923");
    assertThat(firstPayment.getBeneficiaryName()).isEqualTo("TULEVA FONDID AS");
    assertThat(firstPayment.getExternalId()).isEqualTo("2025092900654847-1");

    // Verify second payment (debit transaction)
    SavingFundPayment secondPayment = payments.get(1);
    assertThat(secondPayment.getAmount()).isEqualByComparingTo(new BigDecimal("-0.10"));
    assertThat(secondPayment.getCurrency()).isEqualTo(Currency.EUR);
    assertThat(secondPayment.getDescription()).isEqualTo("bounce-back");
    assertThat(secondPayment.getRemitterIban()).isEqualTo("EE442200221092874625");
    assertThat(secondPayment.getRemitterIdCode()).isEqualTo("14118923");
    assertThat(secondPayment.getRemitterName()).isEqualTo("TULEVA FONDID AS");
    assertThat(secondPayment.getBeneficiaryIban()).isEqualTo("EE157700771001802057");
    assertThat(secondPayment.getBeneficiaryIdCode()).isEqualTo(null);
    assertThat(secondPayment.getBeneficiaryName()).isEqualTo("Jüri Tamm");
    assertThat(secondPayment.getExternalId()).isEqualTo("2025092900673437-1");
  }

  @Test
  void extractPayments_shouldHandleEmptyStatements() {
    // given
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");
    var statement = createBankStatement(account, List.of());

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement);

    // then
    assertThat(payments).isEmpty();
  }

  @Test
  void extractPayments_shouldExtractReceiveBefore() {
    // given
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");
    var creditEntry =
        createCreditEntry(
            new BigDecimal("0.10"),
            "EE157700771001802057",
            "Jüri Tamm",
            "39910273027",
            "39910273027",
            "2025092900654847-1");
    var receiveBefore = java.time.Instant.parse("2025-09-29T15:37:46Z");
    var statement =
        new BankStatement(
            BankStatement.BankStatementType.INTRA_DAY_REPORT,
            account,
            List.of(),
            List.of(creditEntry),
            receiveBefore);

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement);

    // then
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).getReceivedBefore()).isEqualTo(receiveBefore);
  }

  @Test
  void extractPayments_shouldThrowExceptionForUnsupportedCurrency() {
    // given
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");

    var counterParty =
        createCounterPartyDetails("Test Person", "EE123456789012345678", "12345678901");
    var usdEntry =
        new BankStatementEntry(
            counterParty,
            new BigDecimal("100.00"),
            "USD",
            TransactionType.CREDIT,
            "Test payment",
            "test-ref",
            null);

    var statement = createBankStatement(account, List.of(usdEntry));

    // when & then
    assertThatThrownBy(() -> extractor.extractPayments(statement))
        .isInstanceOf(PaymentProcessingException.class)
        .hasMessage("Bank transfer currency not supported: USD");
  }

  @Test
  void extractPayments_shouldNormalizeAmountWithTrailingZeros() {
    // given - amount with trailing zeros (150.100 should become 150.10)
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");

    var creditEntry =
        createCreditEntry(
            new BigDecimal("150.100"),
            "EE157700771001802057",
            "Test Person",
            "12345678901",
            "Test payment",
            "test-ref");

    var statement = createBankStatement(account, List.of(creditEntry));

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement);

    // then
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("150.10"));
    assertThat(payments.get(0).getAmount().scale()).isEqualTo(2);
  }

  @Test
  void extractPayments_shouldThrowExceptionForMoreThan2SignificantDecimalPlaces() {
    // given - amount with more than 2 significant decimal places (150.101)
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");

    var creditEntry =
        createCreditEntry(
            new BigDecimal("150.101"),
            "EE157700771001802057",
            "Test Person",
            "12345678901",
            "Test payment",
            "test-ref");

    var statement = createBankStatement(account, List.of(creditEntry));

    // when & then
    assertThatThrownBy(() -> extractor.extractPayments(statement))
        .isInstanceOf(PaymentProcessingException.class)
        .hasMessageContaining("Amount has more than 2 significant decimal places: 150.101");
  }

  @Test
  void extractPayments_shouldAcceptExactly2DecimalPlaces() {
    // given - amount with exactly 2 decimal places
    var account =
        createBankStatementAccount("EE442200221092874625", "TULEVA FONDID AS", "14118923");

    var creditEntry =
        createCreditEntry(
            new BigDecimal("150.10"),
            "EE157700771001802057",
            "Test Person",
            "12345678901",
            "Test payment",
            "test-ref");

    var statement = createBankStatement(account, List.of(creditEntry));

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement);

    // then
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("150.10"));
  }

  // Helper methods for manual BankStatement construction

  private BankStatement createBankStatement(
      BankStatementAccount account, List<BankStatementEntry> entries) {
    return new BankStatement(
        BankStatement.BankStatementType.INTRA_DAY_REPORT, account, List.of(), entries, null);
  }

  private BankStatementAccount createBankStatementAccount(String iban, String name, String idCode) {
    return new BankStatementAccount(iban, name, idCode);
  }

  private BankStatementEntry createCreditEntry(
      BigDecimal amount,
      String counterPartyIban,
      String counterPartyName,
      String counterPartyIdCode,
      String description,
      String externalId) {
    var counterParty =
        createCounterPartyDetails(counterPartyName, counterPartyIban, counterPartyIdCode);
    return new BankStatementEntry(
        counterParty, amount, "EUR", TransactionType.CREDIT, description, externalId, null);
  }

  private BankStatementEntry createDebitEntry(
      BigDecimal amount,
      String counterPartyIban,
      String counterPartyName,
      String counterPartyIdCode,
      String description,
      String externalId) {
    var counterParty =
        createCounterPartyDetails(counterPartyName, counterPartyIban, counterPartyIdCode);
    return new BankStatementEntry(
        counterParty, amount, "EUR", TransactionType.DEBIT, description, externalId, null);
  }

  private BankStatementEntry.CounterPartyDetails createCounterPartyDetails(
      String name, String iban, String personalCode) {
    return new BankStatementEntry.CounterPartyDetails(name, iban, personalCode);
  }
}
