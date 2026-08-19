package ee.tuleva.onboarding.banking.seb.processor;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementBalance;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.FundBankLedger.UnclassifiedEntryDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

@Slf4j
@NullMarked
@RequiredArgsConstructor
public class PensionFundStatementProcessor {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final PensionFundEntryClassifier classifier;
  private final FundBankLedger fundBankLedger;

  public void process(BankStatement statement, BankAccount account) {
    log.info(
        "Processing pension fund bank statement: account={}, type={}, entries={}",
        account,
        statement.getType(),
        statement.getEntries().size());
    seedOpeningBalance(statement, account);
    statement.getEntries().forEach(entry -> processEntry(entry, account));
  }

  private void seedOpeningBalance(BankStatement statement, BankAccount account) {
    statement.getBalances().stream()
        .filter(balance -> balance.type() == BankStatementBalance.StatementBalanceType.OPEN)
        .findFirst()
        .ifPresent(
            opening ->
                fundBankLedger.seedOpeningBalanceIfFirstStatement(
                    account.fund(), opening.balance(), opening.time()));
  }

  private void processEntry(BankStatementEntry entry, BankAccount account) {
    if (entry.externalId() == null) {
      throw new IllegalStateException(
          "Bank entry without external id: account=%s, amount=%s"
              .formatted(account, entry.amount()));
    }

    var externalReference =
        UUID.nameUUIDFromBytes((account.iban() + ":" + entry.externalId()).getBytes(UTF_8));

    if (fundBankLedger.existsForExternalReference(externalReference)) {
      log.debug(
          "Ledger entry already exists: account={}, externalRef={}", account, externalReference);
      return;
    }

    var fund = account.fund();
    var amount = normalizeAmount(entry.amount());
    var bookingDate = bookingDate(entry, account);

    switch (classifier.classify(entry)) {
      case PensionFundEntryClassifier.InterestReceived() ->
          fundBankLedger.recordInterestReceived(
              fund, amount, externalReference, account.ledgerAccount(), bookingDate);
      case PensionFundEntryClassifier.BankFee() ->
          fundBankLedger.recordBankFee(
              fund, amount, externalReference, account.ledgerAccount(), bookingDate);
      case PensionFundEntryClassifier.BankAdjustment() ->
          fundBankLedger.recordBankAdjustment(
              fund, amount, externalReference, account.ledgerAccount(), bookingDate);
      case PensionFundEntryClassifier.ManagementFeeRebate() ->
          fundBankLedger.recordManagementFeeRebate(
              fund,
              amount,
              externalReference,
              account.ledgerAccount(),
              bookingDate,
              entry.remittanceInformation());
      case PensionFundEntryClassifier.ManagementFeePayment() ->
          fundBankLedger.recordManagementFeePayment(
              fund, amount.negate(), externalReference, entry.remittanceInformation(), bookingDate);
      case PensionFundEntryClassifier.RegistrarContribution() ->
          fundBankLedger.recordRegistrarContribution(
              fund, amount, externalReference, bookingDate, entry.remittanceInformation());
      case PensionFundEntryClassifier.RegistrarPayout() ->
          fundBankLedger.recordRegistrarPayout(
              fund, amount, externalReference, bookingDate, entry.remittanceInformation());
      case PensionFundEntryClassifier.TradeSettlement(var ticker, var units) ->
          fundBankLedger.recordTradeSettlement(
              fund,
              amount,
              signedUnits(units, amount),
              externalReference,
              account.ledgerAccount(),
              ticker.getIsin(),
              ticker.getYahooTicker().split("\\.")[0],
              ticker.getDisplayName(),
              bookingDate);
      case PensionFundEntryClassifier.Unclassified(var reason) -> {
        log.error(
            "Unclassified pension fund bank entry: account={}, externalId={}, amount={}, subFamilyCode={}, reason={}",
            account,
            entry.externalId(),
            entry.amount(),
            entry.subFamilyCode(),
            reason);
        var details = entry.details();
        fundBankLedger.recordUnclassifiedBankEntry(
            fund,
            amount,
            externalReference,
            account.ledgerAccount(),
            bookingDate,
            new UnclassifiedEntryDetails(
                details == null ? null : details.getName(),
                details == null ? null : details.getIban(),
                entry.remittanceInformation(),
                entry.subFamilyCode()));
      }
    }
  }

  private static LocalDate bookingDate(BankStatementEntry entry, BankAccount account) {
    var receivedBefore = entry.receivedBefore();
    if (receivedBefore == null) {
      throw new IllegalStateException(
          "Bank entry without booking time: account=%s, externalId=%s"
              .formatted(account, entry.externalId()));
    }
    return receivedBefore.atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    var normalized = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(normalized) != 0) {
      log.info("Normalized bank entry amount: original={}, normalized={}", amount, normalized);
    }
    return normalized;
  }

  private static BigDecimal signedUnits(BigDecimal units, BigDecimal amount) {
    var scaled = units.abs().setScale(5, RoundingMode.HALF_UP);
    return amount.signum() > 0 ? scaled.negate() : scaled;
  }
}
