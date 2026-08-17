package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PensionFundStatementProcessorTest {

  private static final String TUK75_IBAN = "EE001234567890123475";
  private static final BankAccount TUK75_ACCOUNT =
      new BankAccount(TUK75_IBAN, FUND_INVESTMENT_EUR, TUK75, "gw-test");

  @Mock private PensionFundEntryClassifier classifier;
  @Mock private FundBankLedger fundBankLedger;

  @InjectMocks private PensionFundStatementProcessor processor;

  @Test
  void interestEntry_isRecordedForTheStatementFund() {
    var entry = entry(new BigDecimal("2.00"), "intress");
    when(classifier.classify(entry)).thenReturn(new PensionFundEntryClassifier.InterestReceived());

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordInterestReceived(
            eq(TUK75),
            eq(new BigDecimal("2.00")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void registrarContribution_isRecordedWithRemittanceInformation() {
    var entry = entry(new BigDecimal("1000000.00"), "osakute laekumine");
    when(classifier.classify(entry))
        .thenReturn(new PensionFundEntryClassifier.RegistrarContribution());

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordRegistrarContribution(
            eq(TUK75),
            eq(new BigDecimal("1000000.00")),
            any(UUID.class),
            eq(LocalDate.of(2025, 10, 1)),
            eq("osakute laekumine"));
  }

  @Test
  void registrarPayout_isRecordedWithCamtSignedAmount() {
    var entry = entry(new BigDecimal("-250000.00"), "tagasivõtmine");
    when(classifier.classify(entry)).thenReturn(new PensionFundEntryClassifier.RegistrarPayout());

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordRegistrarPayout(
            eq(TUK75),
            eq(new BigDecimal("-250000.00")),
            any(UUID.class),
            eq(LocalDate.of(2025, 10, 1)),
            eq("tagasivõtmine"));
  }

  @Test
  void managementFeePayment_isRecordedWithPositiveAmount() {
    var entry = entry(new BigDecimal("-742.34"), "Valitsemistasu 02/2026");
    when(classifier.classify(entry))
        .thenReturn(new PensionFundEntryClassifier.ManagementFeePayment());

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordManagementFeePayment(
            eq(TUK75),
            eq(new BigDecimal("742.34")),
            any(UUID.class),
            eq("Valitsemistasu 02/2026"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void sellSettlement_recordsNegativeUnits() {
    var entry = entry(new BigDecimal("50000.00"), "DLA1/BDWTEIA/1450.25/34.477/Sell/");
    when(classifier.classify(entry))
        .thenReturn(
            new PensionFundEntryClassifier.TradeSettlement(
                FundTicker.ISHARES_DEVELOPED_WORLD_ESG_SCREENED, new BigDecimal("1450.25")));

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordTradeSettlement(
            eq(TUK75),
            eq(new BigDecimal("50000.00")),
            eq(new BigDecimal("-1450.25000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("IE00BFG1TM61"),
            eq("0P000152G5"),
            eq("iShares Developed World Screened Index Fund"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void unclassifiedEntry_landsInSuspenseWithClassifierMetadata() {
    var entry =
        entryWithCounterparty(
            new BigDecimal("99.99"), "selgituseta", "Mystery OU", "EE001234567890123499", "OTHR");
    when(classifier.classify(entry))
        .thenReturn(new PensionFundEntryClassifier.Unclassified("unknown counterparty"));

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger)
        .recordUnclassifiedBankEntry(
            eq(TUK75),
            eq(new BigDecimal("99.99")),
            any(UUID.class),
            eq(LocalDate.of(2025, 10, 1)),
            eq(
                new FundBankLedger.UnclassifiedEntryDetails(
                    "Mystery OU", "EE001234567890123499", "selgituseta", "OTHR")));
  }

  @Test
  void alreadyRecordedEntry_isSkippedRegardlessOfClassification() {
    var entry = entry(new BigDecimal("2.00"), "intress");
    when(fundBankLedger.existsForExternalReference(any(UUID.class))).thenReturn(true);

    processor.process(statementWith(entry), TUK75_ACCOUNT);

    verify(fundBankLedger, never()).recordInterestReceived(any(), any(), any(), any(), any());
    verifyNoInteractions(classifier);
  }

  @Test
  void entryWithoutExternalId_failsTheStatement() {
    var entry =
        new BankStatementEntry(
            null,
            new BigDecimal("10.00"),
            "EUR",
            TransactionType.CREDIT,
            "tundmatu",
            null,
            null,
            "OTHR",
            Instant.parse("2025-10-01T20:59:59.999999Z"));

    assertThatThrownBy(() -> processor.process(statementWith(entry), TUK75_ACCOUNT))
        .isInstanceOf(IllegalStateException.class);
  }

  private BankStatement statementWith(BankStatementEntry... entries) {
    return new BankStatement(
        BankStatementType.HISTORIC_STATEMENT,
        new BankStatementAccount(TUK75_IBAN, "Tuleva Maailma Aktsiate Pensionifond", "14118923"),
        List.of(),
        List.of(entries));
  }

  private BankStatementEntry entry(BigDecimal amount, String remittanceInformation) {
    return new BankStatementEntry(
        null,
        amount,
        "EUR",
        amount.signum() >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        remittanceInformation,
        "entry-ref-1",
        null,
        null,
        Instant.parse("2025-10-01T20:59:59.999999Z"));
  }

  private BankStatementEntry entryWithCounterparty(
      BigDecimal amount,
      String remittanceInformation,
      String counterpartyName,
      String counterpartyIban,
      String subFamilyCode) {
    return new BankStatementEntry(
        new BankStatementEntry.CounterPartyDetails(counterpartyName, counterpartyIban, null),
        amount,
        "EUR",
        amount.signum() >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        remittanceInformation,
        "entry-ref-1",
        null,
        subFamilyCode,
        Instant.parse("2025-10-01T20:59:59.999999Z"));
  }
}
