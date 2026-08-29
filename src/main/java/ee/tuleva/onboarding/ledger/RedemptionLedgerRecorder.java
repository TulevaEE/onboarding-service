package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.CUSTOMER_IBAN;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.NAV_PER_UNIT;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.REDEMPTION_REQUEST_ID;

import ee.tuleva.onboarding.party.PartyId;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedemptionLedgerRecorder {

  private final SavingsFundLedgerAccounts accounts;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  LedgerTransaction reserveFundUnitsForRedemption(
      PartyId party, BigDecimal fundUnits, UUID externalReference) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userUnitsAccount = accounts.getUserUnitsAccount(ledgerParty);
    LedgerAccount userUnitsReservedAccount = accounts.getUserUnitsReservedAccount(ledgerParty);

    Map<String, Object> metadata = accounts.partyMetadata(party, REDEMPTION_RESERVED);

    return ledgerTransactionService.createTransaction(
        REDEMPTION_RESERVED,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userUnitsAccount, fundUnits),
        accounts.entry(userUnitsReservedAccount, fundUnits.negate()));
  }

  @Transactional
  LedgerTransaction cancelRedemptionReservation(
      PartyId party, BigDecimal fundUnits, UUID externalReference) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userUnitsAccount = accounts.getUserUnitsAccount(ledgerParty);
    LedgerAccount userUnitsReservedAccount = accounts.getUserUnitsReservedAccount(ledgerParty);

    Map<String, Object> metadata = accounts.partyMetadata(party, REDEMPTION_CANCELLED);

    return ledgerTransactionService.createTransaction(
        REDEMPTION_CANCELLED,
        Instant.now(clock),
        externalReference,
        metadata,
        accounts.entry(userUnitsReservedAccount, fundUnits),
        accounts.entry(userUnitsAccount, fundUnits.negate()));
  }

  @Transactional
  LedgerTransaction redeemFundUnitsFromReserved(
      PartyId party,
      BigDecimal fundUnits,
      BigDecimal cashAmount,
      BigDecimal navPerUnit,
      UUID redemptionRequestId) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userUnitsReservedAccount = accounts.getUserUnitsReservedAccount(ledgerParty);
    LedgerAccount userCashRedemptionAccount = accounts.getUserCashRedemptionAccount(ledgerParty);
    LedgerAccount unitsOutstandingAccount = accounts.getFundUnitsOutstandingAccount();
    LedgerAccount userRedemptionsAccount = accounts.getUserRedemptionsAccount(ledgerParty);

    var metadataBuilder = new HashMap<>(accounts.partyMetadata(party, REDEMPTION_REQUEST));
    metadataBuilder.put(NAV_PER_UNIT.getKey(), navPerUnit);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.getKey(), redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_REQUEST,
        Instant.now(clock),
        redemptionRequestId,
        metadataBuilder,
        accounts.entry(userUnitsReservedAccount, fundUnits),
        accounts.entry(unitsOutstandingAccount, fundUnits.negate()),
        accounts.entry(userCashRedemptionAccount, cashAmount.negate()),
        accounts.entry(userRedemptionsAccount, cashAmount));
  }

  @Transactional
  LedgerTransaction transferFromFundAccount(BigDecimal amount, UUID externalReference) {
    return transferFromFundAccount(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  LedgerTransaction transferFromFundAccount(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount fundCashAccount = accounts.getFundInvestmentCashClearingAccount();
    LedgerAccount payoutsCashAccount = accounts.getPayoutsCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.getKey(), FUND_CASH_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_CASH_TRANSFER,
        accounts.transactionDate(bookingDate),
        externalReference,
        metadata,
        accounts.entry(fundCashAccount, amount.negate()),
        accounts.entry(payoutsCashAccount, amount));
  }

  @Transactional
  LedgerTransaction recordRedemptionPayout(
      PartyId party, BigDecimal amount, String customerIban, UUID redemptionRequestId) {
    return recordRedemptionPayout(
        party, amount, customerIban, redemptionRequestId, LocalDate.now(clock));
  }

  @Transactional
  LedgerTransaction recordRedemptionPayout(
      PartyId party,
      BigDecimal amount,
      String customerIban,
      UUID redemptionRequestId,
      LocalDate bookingDate) {
    LedgerParty ledgerParty = accounts.getParty(party);
    LedgerAccount userCashRedemptionAccount = accounts.getUserCashRedemptionAccount(ledgerParty);
    LedgerAccount payoutsCashAccount = accounts.getPayoutsCashClearingAccount();

    var metadataBuilder = new HashMap<>(accounts.partyMetadata(party, REDEMPTION_PAYOUT));
    metadataBuilder.put(CUSTOMER_IBAN.getKey(), customerIban);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.getKey(), redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_PAYOUT,
        accounts.transactionDate(bookingDate),
        redemptionRequestId,
        metadataBuilder,
        accounts.entry(payoutsCashAccount, amount.negate()),
        accounts.entry(userCashRedemptionAccount, amount));
  }
}
