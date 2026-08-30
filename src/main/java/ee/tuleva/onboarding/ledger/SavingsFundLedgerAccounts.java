package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.PARTY_CODE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.PARTY_TYPE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.time.temporal.ChronoUnit.MICROS;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundLedgerAccounts {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final Clock clock;

  LedgerAccount resolveSystemAccount(String accountName) {
    try {
      return getSystemAccount(SystemAccount.valueOf(accountName));
    } catch (IllegalArgumentException e) {
      var systemAccount = SystemAccount.fromAccountName(accountName);
      return findOrCreateInstrumentAccount(systemAccount, accountName);
    }
  }

  LedgerAccount findOrCreateInstrumentAccount(SystemAccount systemAccount, String accountName) {
    return ledgerAccountService
        .findSystemAccountByName(
            accountName, systemAccount.getAccountType(), systemAccount.getAssetType())
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    accountName, systemAccount.getAccountType(), systemAccount.getAssetType()));
  }

  LedgerAccount resolvePartyAccount(PartyRef party, UserAccount userAccount) {
    LedgerParty ledgerParty =
        ledgerPartyService
            .getParty(party.code(), party.type())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Ledger party not found: partyCode=" + party.code()));
    return getUserAccount(ledgerParty, userAccount);
  }

  Instant transactionDate(LocalDate bookingDate) {
    Instant now = Instant.now(clock);
    if (now.atZone(ESTONIAN_ZONE).toLocalDate().equals(bookingDate)) {
      return now;
    }
    return bookingDate.atTime(LocalTime.MAX).atZone(ESTONIAN_ZONE).toInstant().truncatedTo(MICROS);
  }

  LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }

  Map<String, Object> partyMetadata(PartyRef party, TransactionType transactionType) {
    return Map.of(
        OPERATION_TYPE.getKey(), transactionType.name(),
        PARTY_CODE.getKey(), party.code(),
        PARTY_TYPE.getKey(), party.type().name());
  }

  LedgerParty getParty(PartyRef party) {
    return ledgerPartyService.getOrCreate(party.code(), party.type());
  }

  LedgerAccount getUserAccount(LedgerParty owner, UserAccount userAccount) {
    return ledgerAccountService
        .findUserAccount(owner, userAccount)
        .orElseGet(() -> ledgerAccountService.createUserAccount(owner, userAccount));
  }

  LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, TKF100)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, TKF100));
  }

  LedgerAccount getUserCashAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH);
  }

  LedgerAccount getUserCashReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_RESERVED);
  }

  LedgerAccount getUserCashRedemptionAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_REDEMPTION);
  }

  LedgerAccount getUserUnitsAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS);
  }

  LedgerAccount getUserUnitsReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS_RESERVED);
  }

  LedgerAccount getUserSubscriptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, SUBSCRIPTIONS);
  }

  LedgerAccount getUserRedemptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, REDEMPTIONS);
  }

  LedgerAccount getIncomingPaymentsClearingAccount() {
    return getSystemAccount(INCOMING_PAYMENTS_CLEARING);
  }

  LedgerAccount getUnreconciledBankReceiptsAccount() {
    return getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
  }

  LedgerAccount getFundInvestmentCashClearingAccount() {
    return getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);
  }

  LedgerAccount getFundUnitsOutstandingAccount() {
    return getSystemAccount(FUND_UNITS_OUTSTANDING);
  }

  LedgerAccount getPayoutsCashClearingAccount() {
    return getSystemAccount(PAYOUTS_CASH_CLEARING);
  }
}
