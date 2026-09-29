package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_ACQUISITION_COST_EUR;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_CODE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_TYPE;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.RoundingMode.HALF_UP;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
  LedgerTransaction recordUnitTransfer(UnitTransferInstruction instruction) {
    rejectUnrecordableAcquisitionCost(instruction.recipientAcquisitionCost());

    var alreadyRecorded =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            instruction.externalReference(), UNIT_TRANSFER);
    if (alreadyRecorded.isPresent()) {
      return alreadyRecorded.get();
    }

    PartyRef from = instruction.from();
    PartyRef to = instruction.to();
    Involved involved = resolve(from, to);
    accounts.lockAccountsOfBothParties(from, to);
    UnitTransferQuote quote = quote(from, to, instruction.fundUnits(), involved);

    Map<String, Object> metadata = new HashMap<>(accounts.partyMetadata(from, UNIT_TRANSFER));
    metadata.put(RECIPIENT_CODE.getKey(), to.code());
    metadata.put(RECIPIENT_TYPE.getKey(), to.type().name());
    metadata.put(RECIPIENT_ACQUISITION_COST_EUR.getKey(), instruction.recipientAcquisitionCost());

    return ledgerTransactionService.createTransaction(
        UNIT_TRANSFER,
        Instant.now(clock),
        instruction.externalReference(),
        metadata,
        accounts.entry(involved.fromUnits(), quote.fundUnits()),
        accounts.entry(involved.toUnits(), quote.fundUnits().negate()),
        accounts.entry(involved.fromSubscriptions(), quote.contributionMoved()),
        accounts.entry(involved.toSubscriptions(), quote.contributionMoved().negate()));
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
    BigDecimal paidIn = holding(involved.fromSubscriptions());
    GiverCostBasis costBasis = costBasisOf(involved);
    rejectAHistoryThatDoesNotAddUp(from, costBasis, ownedUnits);
    rejectACostThatCannotFollowTheUnits(from, costBasis, paidIn);

    return new UnitTransferQuote(
        transferredUnits,
        availableUnits.subtract(transferredUnits),
        holding(involved.toUnits()).add(transferredUnits),
        paidIn,
        ownedUnits,
        costBasis.remainingCost(),
        costBasis.costOf(transferredUnits));
  }

  private static void rejectAHistoryThatDoesNotAddUp(
      PartyRef from, GiverCostBasis costBasis, BigDecimal ownedUnits) {
    if (costBasis.remainingUnits().compareTo(ownedUnits) != 0) {
      throw new IllegalStateException(
          "The giver's unit history does not add up to the units they hold: partyCode="
              + from.code()
              + ", replayedUnits="
              + costBasis.remainingUnits().toPlainString()
              + ", heldUnits="
              + ownedUnits.toPlainString());
    }
  }

  private static void rejectACostThatCannotFollowTheUnits(
      PartyRef from, GiverCostBasis costBasis, BigDecimal paidIn) {
    if (costBasis.remainingCost().signum() < 0) {
      throw new IllegalStateException(
          "More was taken back from the giver than their remaining units cost: partyCode="
              + from.code()
              + ", remainingCost="
              + costBasis.remainingCost().toPlainString());
    }
    if (costBasis.remainingCost().compareTo(paidIn) > 0) {
      throw new IllegalStateException(
          "The giver's remaining units cost more than what is left of what they paid in:"
              + " partyCode="
              + from.code()
              + ", remainingCost="
              + costBasis.remainingCost().toPlainString()
              + ", paidIn="
              + paidIn.toPlainString());
    }
  }

  private static void rejectUnrecordableAcquisitionCost(BigDecimal cost) {
    if (cost.signum() < 0) {
      throw new IllegalArgumentException(
          "The recipient's acquisition cost cannot be negative: recipientAcquisitionCostEur="
              + cost.toPlainString());
    }
    if (cost.stripTrailingZeros().scale() > EUR.getMaxPrecision()) {
      throw new IllegalArgumentException(
          "The recipient's acquisition cost is held in cents, so it cannot be finer than that:"
              + " recipientAcquisitionCostEur="
              + cost.toPlainString());
    }
  }

  private Involved resolve(PartyRef from, PartyRef to) {
    return new Involved(
        accounts.resolvePartyAccount(from, FUND_UNITS),
        accounts.resolvePartyAccount(from, FUND_UNITS_RESERVED),
        accounts.resolvePartyAccount(from, SUBSCRIPTIONS),
        accounts.resolvePartyAccount(to, FUND_UNITS),
        accounts.resolvePartyAccount(to, SUBSCRIPTIONS));
  }

  private BigDecimal holding(LedgerAccount account) {
    return accounts.holding(account);
  }

  private GiverCostBasis costBasisOf(Involved involved) {
    return accounts.costBasisOf(
        involved.fromUnits(), involved.fromReservedUnits(), involved.fromSubscriptions());
  }

  private record Involved(
      LedgerAccount fromUnits,
      LedgerAccount fromReservedUnits,
      LedgerAccount fromSubscriptions,
      LedgerAccount toUnits,
      LedgerAccount toSubscriptions) {}
}
