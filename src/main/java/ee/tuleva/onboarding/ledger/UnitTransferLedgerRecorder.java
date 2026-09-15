package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_CODE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.TRANSFERRED_SUBSCRIPTIONS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SavingsFundStatementService reads paid-in amounts straight off SUBSCRIPTIONS, so units must never
 * move without their proportional share of that balance.
 *
 * <p>TrusteeReportRepository sums SUBSCRIPTIONS across user accounts per day without filtering on
 * transaction type, so both euro legs must stay in one transaction on one date to cancel there.
 *
 * <p>SUBSCRIPTIONS covers every unit a party owns, and reserving for redemption moves units out of
 * FUND_UNITS into FUND_UNITS_RESERVED, so the basis is apportioned over both accounts. Apportioning
 * over the free units alone would hand away the basis belonging to the reserved ones.
 */
@Component
@RequiredArgsConstructor
class UnitTransferLedgerRecorder {

  private final SavingsFundLedgerAccounts accounts;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  UnitTransferQuote quote(PartyRef from, PartyRef to, BigDecimal units) {
    return quote(from, to, units, resolve(from, to));
  }

  @Transactional
  LedgerTransaction recordUnitTransfer(
      PartyRef from, PartyRef to, BigDecimal units, UUID externalReference) {
    var alreadyRecorded =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, UNIT_TRANSFER);
    if (alreadyRecorded.isPresent()) {
      return alreadyRecorded.get();
    }

    Involved involved = resolve(from, to);
    UnitTransferQuote quote = quote(from, to, units, involved);

    Map<String, Object> metadata = new HashMap<>(accounts.partyMetadata(from, UNIT_TRANSFER));
    metadata.put(RECIPIENT_CODE.getKey(), to.code());
    metadata.put(RECIPIENT_TYPE.getKey(), to.type().name());
    metadata.put(TRANSFERRED_SUBSCRIPTIONS.getKey(), quote.subscriptionsEur());

    return ledgerTransactionService.createTransaction(
        UNIT_TRANSFER,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(involved.fromUnits(), quote.fundUnits()),
        accounts.entry(involved.toUnits(), quote.fundUnits().negate()),
        accounts.entry(involved.fromSubscriptions(), quote.subscriptionsEur()),
        accounts.entry(involved.toSubscriptions(), quote.subscriptionsEur().negate()));
  }

  private UnitTransferQuote quote(PartyRef from, PartyRef to, BigDecimal units, Involved involved) {
    if (from.equals(to)) {
      throw new IllegalArgumentException(
          "Cannot transfer units to the same party: partyCode=" + from.code());
    }
    if (units.signum() <= 0) {
      throw new IllegalArgumentException("Units to transfer must be positive, but was: " + units);
    }
    if (units.stripTrailingZeros().scale() > FUND_UNIT.getMaxPrecision()) {
      throw new IllegalArgumentException(
          "Units to transfer are finer than the fund prices them: requested="
              + units.toPlainString()
              + ", decimals="
              + FUND_UNIT.getMaxPrecision());
    }

    BigDecimal transferredUnits = units.setScale(FUND_UNIT.getMaxPrecision(), HALF_UP);
    BigDecimal availableUnits = holding(involved.fromUnits());

    if (transferredUnits.compareTo(availableUnits) > 0) {
      throw new IllegalStateException(
          "Cannot transfer more units than the party holds: partyCode="
              + from.code()
              + ", requested="
              + transferredUnits
              + ", available="
              + availableUnits);
    }

    BigDecimal ownedUnits = availableUnits.add(holding(involved.fromReservedUnits()));
    return new UnitTransferQuote(
        transferredUnits,
        proportionalSubscriptions(involved.fromSubscriptions(), transferredUnits, ownedUnits),
        availableUnits.subtract(transferredUnits),
        holding(involved.toUnits()).add(transferredUnits));
  }

  private Involved resolve(PartyRef from, PartyRef to) {
    return new Involved(
        accounts.resolvePartyAccount(from, FUND_UNITS),
        accounts.resolvePartyAccount(from, FUND_UNITS_RESERVED),
        accounts.resolvePartyAccount(from, SUBSCRIPTIONS),
        accounts.resolvePartyAccount(to, FUND_UNITS),
        accounts.resolvePartyAccount(to, SUBSCRIPTIONS));
  }

  private BigDecimal proportionalSubscriptions(
      LedgerAccount fromSubscriptions, BigDecimal transferredUnits, BigDecimal ownedUnits) {
    BigDecimal paidIn = holding(fromSubscriptions);

    if (paidIn.signum() == 0 || ownedUnits.signum() == 0) {
      return ZERO.setScale(EUR.getMaxPrecision());
    }
    if (transferredUnits.compareTo(ownedUnits) == 0) {
      return paidIn;
    }
    return paidIn.multiply(transferredUnits).divide(ownedUnits, EUR.getMaxPrecision(), HALF_UP);
  }

  /** Holdings are stored as negative liabilities; callers think in positive amounts. */
  private BigDecimal holding(LedgerAccount account) {
    return account.getBalance().negate();
  }

  private record Involved(
      LedgerAccount fromUnits,
      LedgerAccount fromReservedUnits,
      LedgerAccount fromSubscriptions,
      LedgerAccount toUnits,
      LedgerAccount toSubscriptions) {}
}
