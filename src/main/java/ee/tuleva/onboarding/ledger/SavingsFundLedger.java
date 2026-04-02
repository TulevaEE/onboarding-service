package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static java.time.temporal.ChronoUnit.MICROS;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import ee.tuleva.onboarding.party.PartyId;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ledger service for Tuleva savings fund transactions.
 *
 * <h2>Subscription Flow (buying fund units)</h2>
 *
 * <pre>
 * 1. recordPaymentReceived         INCOMING_PAYMENTS_CLEARING → User:CASH
 * 2. reservePaymentForSubscription User:CASH → User:CASH_RESERVED
 * 3. issueFundUnitsFromReserved    User:CASH_RESERVED → User:SUBSCRIPTIONS
 *                                  FUND_UNITS_OUTSTANDING → User:FUND_UNITS
 * 4. transferToFundAccount         INCOMING_PAYMENTS_CLEARING → FUND_INVESTMENT_CASH_CLEARING
 * </pre>
 *
 * <h2>Subscription Cancellation Flow (before fund units issued)</h2>
 *
 * <pre>
 * 1. reservePaymentForCancellation User:CASH → User:CASH_RESERVED
 * 2. recordPaymentCancelled        User:CASH_RESERVED → INCOMING_PAYMENTS_CLEARING
 * </pre>
 *
 * <h2>Redemption Flow (selling fund units)</h2>
 *
 * <pre>
 * 1. reserveFundUnitsForRedemption User:FUND_UNITS → User:FUND_UNITS_RESERVED
 * 2. redeemFundUnitsFromReserved   User:FUND_UNITS_RESERVED → FUND_UNITS_OUTSTANDING
 *                                  User:CASH_REDEMPTION → User:REDEMPTIONS
 * 3. transferFromFundAccount       FUND_INVESTMENT_CASH_CLEARING → PAYOUTS_CASH_CLEARING
 * 4. recordRedemptionPayout        PAYOUTS_CASH_CLEARING → User:CASH_REDEMPTION
 * </pre>
 *
 * <h2>Redemption Cancellation Flow (before payout)</h2>
 *
 * <pre>
 * 1. cancelRedemptionReservation   User:FUND_UNITS_RESERVED → User:FUND_UNITS
 * </pre>
 *
 * <h2>Unattributed Payment Flows</h2>
 *
 * <pre>
 * recordUnattributedPayment        INCOMING_PAYMENTS_CLEARING → UNRECONCILED_BANK_RECEIPTS
 * bounceBackUnattributedPayment    UNRECONCILED_BANK_RECEIPTS → INCOMING_PAYMENTS_CLEARING
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsFundLedger {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Getter
  @AllArgsConstructor
  public enum MetadataKey {
    OPERATION_TYPE("operationType"),
    PARTY_CODE("partyCode"),
    PARTY_TYPE("partyType"),
    EXTERNAL_REFERENCE("externalReference"),
    PAYER_IBAN("payerIban"),
    CUSTOMER_IBAN("customerIban"),
    NAV_PER_UNIT("navPerUnit"),
    REDEMPTION_REQUEST_ID("redemptionRequestId"),
    DESCRIPTION("description"),
    INSTRUMENT("instrument"),
    TICKER("ticker"),
    DISPLAY_NAME("displayName");

    private final String key;
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      PartyId party, BigDecimal amount, UUID externalReference) {
    return recordPaymentReceived(party, amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordPaymentReceived(
      PartyId party, BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashAccount = getUserCashAccount(ledgerParty);
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = partyMetadata(party, PAYMENT_RECEIVED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_RECEIVED,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(userCashAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reservePaymentForCancellation(
      PartyId party, BigDecimal amount, UUID externalReference) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashAccount = getUserCashAccount(ledgerParty);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(ledgerParty);

    Map<String, Object> metadata = partyMetadata(party, PAYMENT_CANCEL_REQUESTED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCEL_REQUESTED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashAccount, amount),
        entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordPaymentCancelled(
      PartyId party, BigDecimal amount, UUID externalReference) {
    boolean unattributedPaymentExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);

    if (unattributedPaymentExists) {
      return bounceBackUnattributedPayment(amount, externalReference);
    }

    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_CANCELLED);
    if (existing.isPresent()) {
      log.error(
          "Duplicate PAYMENT_CANCELLED prevented: externalReference={}",
          externalReference,
          new Exception("Duplicate caller stacktrace"));
      return existing.get();
    }

    ensurePaymentReceivedExists(party, amount, externalReference);
    ensureReservationExists(party, amount, externalReference);

    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(ledgerParty);
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = partyMetadata(party, PAYMENT_CANCELLED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_CANCELLED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashReservedAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  private void ensurePaymentReceivedExists(
      PartyId party, BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_RECEIVED);
    if (!alreadyRecorded) {
      recordPaymentReceived(party, amount, externalReference);
    }
  }

  private void ensureReservationExists(PartyId party, BigDecimal amount, UUID externalReference) {
    boolean reservationAlreadyExists =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_CANCEL_REQUESTED);
    if (!reservationAlreadyExists) {
      reservePaymentForCancellation(party, amount, externalReference);
    }
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(BigDecimal amount, UUID externalReference) {
    return recordUnattributedPayment(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordUnattributedPayment(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, UNATTRIBUTED_PAYMENT.name());

    return ledgerTransactionService.createTransaction(
        UNATTRIBUTED_PAYMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount),
        entry(unreconciledAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction reservePaymentForSubscription(
      PartyId party, BigDecimal amount, UUID externalReference) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashAccount = getUserCashAccount(ledgerParty);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(ledgerParty);

    Map<String, Object> metadata = partyMetadata(party, PAYMENT_RESERVED);

    return ledgerTransactionService.createTransaction(
        PAYMENT_RESERVED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashAccount, amount),
        entry(userCashReservedAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction issueFundUnitsFromReserved(
      PartyId party,
      BigDecimal cashAmount,
      BigDecimal fundUnits,
      BigDecimal navPerUnit,
      UUID externalReference) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashReservedAccount = getUserCashReservedAccount(ledgerParty);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(ledgerParty);
    LedgerAccount userSubscriptionsAccount = getUserSubscriptionsAccount(ledgerParty);
    LedgerAccount unitsOutstandingAccount = getFundUnitsOutstandingAccount();

    var metadata = new HashMap<>(partyMetadata(party, FUND_SUBSCRIPTION));
    metadata.put(NAV_PER_UNIT.key, navPerUnit);

    return ledgerTransactionService.createTransaction(
        FUND_SUBSCRIPTION,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userCashReservedAccount, cashAmount),
        entry(userSubscriptionsAccount, cashAmount.negate()),
        entry(userUnitsAccount, fundUnits.negate()),
        entry(unitsOutstandingAccount, fundUnits));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(BigDecimal amount, UUID externalReference) {
    return transferToFundAccount(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction transferToFundAccount(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();
    LedgerAccount fundCashAccount = getFundInvestmentCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_TRANSFER,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(incomingPaymentsAccount, amount.negate()),
        entry(fundCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction bounceBackUnattributedPayment(
      BigDecimal amount, UUID externalReference) {
    var existing =
        ledgerTransactionService.findByExternalReferenceAndTransactionType(
            externalReference, PAYMENT_BOUNCE_BACK);
    if (existing.isPresent()) {
      log.error(
          "Duplicate PAYMENT_BOUNCE_BACK prevented: externalReference={}",
          externalReference,
          new Exception("Duplicate caller stacktrace"));
      return existing.get();
    }

    ensureUnattributedPaymentRecorded(amount, externalReference);

    LedgerAccount unreconciledAccount = getUnreconciledBankReceiptsAccount();
    LedgerAccount incomingPaymentsAccount = getIncomingPaymentsClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, PAYMENT_BOUNCE_BACK.name());

    return ledgerTransactionService.createTransaction(
        PAYMENT_BOUNCE_BACK,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(unreconciledAccount, amount),
        entry(incomingPaymentsAccount, amount.negate()));
  }

  private void ensureUnattributedPaymentRecorded(BigDecimal amount, UUID externalReference) {
    boolean alreadyRecorded =
        ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            externalReference, UNATTRIBUTED_PAYMENT);
    if (!alreadyRecorded) {
      recordUnattributedPayment(amount, externalReference);
    }
  }

  @Transactional
  public LedgerTransaction reserveFundUnitsForRedemption(
      PartyId party, BigDecimal fundUnits, UUID externalReference) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(ledgerParty);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(ledgerParty);

    Map<String, Object> metadata = partyMetadata(party, REDEMPTION_RESERVED);

    return ledgerTransactionService.createTransaction(
        REDEMPTION_RESERVED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userUnitsAccount, fundUnits),
        entry(userUnitsReservedAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction cancelRedemptionReservation(
      PartyId party, BigDecimal fundUnits, UUID externalReference) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userUnitsAccount = getUserUnitsAccount(ledgerParty);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(ledgerParty);

    Map<String, Object> metadata = partyMetadata(party, REDEMPTION_CANCELLED);

    return ledgerTransactionService.createTransaction(
        REDEMPTION_CANCELLED,
        Instant.now(clock),
        externalReference,
        metadata,
        entry(userUnitsReservedAccount, fundUnits),
        entry(userUnitsAccount, fundUnits.negate()));
  }

  @Transactional
  public LedgerTransaction redeemFundUnitsFromReserved(
      PartyId party,
      BigDecimal fundUnits,
      BigDecimal cashAmount,
      BigDecimal navPerUnit,
      UUID redemptionRequestId) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userUnitsReservedAccount = getUserUnitsReservedAccount(ledgerParty);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(ledgerParty);
    LedgerAccount unitsOutstandingAccount = getFundUnitsOutstandingAccount();
    LedgerAccount userRedemptionsAccount = getUserRedemptionsAccount(ledgerParty);

    var metadataBuilder = new HashMap<>(partyMetadata(party, REDEMPTION_REQUEST));
    metadataBuilder.put(NAV_PER_UNIT.key, navPerUnit);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.key, redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_REQUEST,
        Instant.now(clock),
        redemptionRequestId,
        metadataBuilder,
        entry(userUnitsReservedAccount, fundUnits),
        entry(unitsOutstandingAccount, fundUnits.negate()),
        entry(userCashRedemptionAccount, cashAmount.negate()),
        entry(userRedemptionsAccount, cashAmount));
  }

  @Transactional
  public LedgerTransaction transferFromFundAccount(BigDecimal amount, UUID externalReference) {
    return transferFromFundAccount(amount, externalReference, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction transferFromFundAccount(
      BigDecimal amount, UUID externalReference, LocalDate bookingDate) {
    LedgerAccount fundCashAccount = getFundInvestmentCashClearingAccount();
    LedgerAccount payoutsCashAccount = getPayoutsCashClearingAccount();

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, FUND_CASH_TRANSFER.name());

    return ledgerTransactionService.createTransaction(
        FUND_CASH_TRANSFER,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(fundCashAccount, amount.negate()),
        entry(payoutsCashAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      PartyId party, BigDecimal amount, String customerIban, UUID redemptionRequestId) {
    return recordRedemptionPayout(
        party, amount, customerIban, redemptionRequestId, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordRedemptionPayout(
      PartyId party,
      BigDecimal amount,
      String customerIban,
      UUID redemptionRequestId,
      LocalDate bookingDate) {
    LedgerParty ledgerParty = getParty(party);
    LedgerAccount userCashRedemptionAccount = getUserCashRedemptionAccount(ledgerParty);
    LedgerAccount payoutsCashAccount = getPayoutsCashClearingAccount();

    var metadataBuilder = new HashMap<>(partyMetadata(party, REDEMPTION_PAYOUT));
    metadataBuilder.put(CUSTOMER_IBAN.key, customerIban);
    if (redemptionRequestId != null) {
      metadataBuilder.put(REDEMPTION_REQUEST_ID.key, redemptionRequestId);
    }

    return ledgerTransactionService.createTransaction(
        REDEMPTION_PAYOUT,
        transactionDate(bookingDate),
        redemptionRequestId,
        metadataBuilder,
        entry(payoutsCashAccount, amount.negate()),
        entry(userCashRedemptionAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordAdjustment(
      String debitAccountName,
      PartyId debitParty,
      String creditAccountName,
      PartyId creditParty,
      BigDecimal amount,
      UUID externalReference,
      String description) {
    boolean debitIsParty = debitParty != null;
    boolean creditIsParty = creditParty != null;

    if (debitIsParty && creditIsParty && !debitParty.equals(creditParty)) {
      throw new IllegalArgumentException(
          "Both accounts must belong to the same party or at least one must be a system account");
    }

    LedgerAccount debitAccount =
        debitIsParty
            ? resolvePartyAccount(debitParty, UserAccount.valueOf(debitAccountName))
            : resolveSystemAccount(debitAccountName);

    LedgerAccount creditAccount =
        creditIsParty
            ? resolvePartyAccount(creditParty, UserAccount.valueOf(creditAccountName))
            : resolveSystemAccount(creditAccountName);

    var metadataBuilder = new HashMap<String, Object>();
    metadataBuilder.put(OPERATION_TYPE.key, ADJUSTMENT.name());
    if (description != null) {
      metadataBuilder.put(DESCRIPTION.key, description);
    }

    return ledgerTransactionService.createTransaction(
        ADJUSTMENT,
        Instant.now(clock),
        externalReference,
        metadataBuilder,
        entry(debitAccount, amount),
        entry(creditAccount, amount.negate()));
  }

  private LedgerAccount resolveSystemAccount(String accountName) {
    try {
      return getSystemAccount(SystemAccount.valueOf(accountName));
    } catch (IllegalArgumentException e) {
      var systemAccount = SystemAccount.fromAccountName(accountName);
      return findOrCreateInstrumentAccount(systemAccount, accountName);
    }
  }

  private LedgerAccount findOrCreateInstrumentAccount(
      SystemAccount systemAccount, String accountName) {
    return ledgerAccountService
        .findSystemAccountByName(
            accountName, systemAccount.getAccountType(), systemAccount.getAssetType())
        .orElseGet(
            () ->
                ledgerAccountService.createSystemAccount(
                    accountName, systemAccount.getAccountType(), systemAccount.getAssetType()));
  }

  private LedgerAccount resolvePartyAccount(PartyId party, UserAccount userAccount) {
    var partyType = PartyType.valueOf(party.type().name());
    LedgerParty ledgerParty =
        ledgerPartyService
            .getParty(party.code(), partyType)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Ledger party not found: partyCode=" + party.code()));
    return getUserAccount(ledgerParty, userAccount);
  }

  @Transactional
  public LedgerTransaction recordManagementFeePayment(
      BigDecimal amount, UUID externalReference, String description) {
    return recordManagementFeePayment(amount, externalReference, description, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordManagementFeePayment(
      BigDecimal amount, UUID externalReference, String description, LocalDate bookingDate) {
    LedgerAccount managementFeeAccount = getSystemAccount(MANAGEMENT_FEE);
    LedgerAccount clearingAccount = getFundInvestmentCashClearingAccount();

    Map<String, Object> metadata =
        Map.of(OPERATION_TYPE.key, MANAGEMENT_FEE_PAYMENT.name(), DESCRIPTION.key, description);

    return ledgerTransactionService.createTransaction(
        MANAGEMENT_FEE_PAYMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(managementFeeAccount, amount),
        entry(clearingAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordBankFee(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    return recordBankFee(amount, externalReference, clearingAccount, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordBankFee(
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount bankFeeExpenseAccount = getSystemAccount(SystemAccount.BANK_FEE);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, TransactionType.BANK_FEE.name());

    return ledgerTransactionService.createTransaction(
        TransactionType.BANK_FEE,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(bankFeeExpenseAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  @Transactional
  public LedgerTransaction recordInterestReceived(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    return recordInterestReceived(amount, externalReference, clearingAccount, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordInterestReceived(
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);
    LedgerAccount interestIncomeAccount = getSystemAccount(INTEREST_INCOME);

    Map<String, Object> metadata = Map.of(OPERATION_TYPE.key, INTEREST_RECEIVED.name());

    return ledgerTransactionService.createTransaction(
        INTEREST_RECEIVED,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(interestIncomeAccount, amount.negate()));
  }

  @Transactional
  public LedgerTransaction recordBankAdjustment(
      BigDecimal amount, UUID externalReference, SystemAccount clearingAccount) {
    return recordBankAdjustment(amount, externalReference, clearingAccount, LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordBankAdjustment(
      BigDecimal amount,
      UUID externalReference,
      SystemAccount clearingAccount,
      LocalDate bookingDate) {
    LedgerAccount bankAdjustmentAccount = getSystemAccount(SystemAccount.BANK_ADJUSTMENT);
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);

    Map<String, Object> metadata =
        Map.of(OPERATION_TYPE.key, TransactionType.BANK_ADJUSTMENT.name());

    return ledgerTransactionService.createTransaction(
        TransactionType.BANK_ADJUSTMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(bankAdjustmentAccount, amount.negate()),
        entry(clearingLedgerAccount, amount));
  }

  private Instant transactionDate(LocalDate bookingDate) {
    Instant now = Instant.now(clock);
    if (now.atZone(ESTONIAN_ZONE).toLocalDate().equals(bookingDate)) {
      return now;
    }
    return bookingDate.atTime(LocalTime.MAX).atZone(ESTONIAN_ZONE).toInstant().truncatedTo(MICROS);
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }

  private Map<String, Object> partyMetadata(PartyId party, TransactionType transactionType) {
    return Map.of(
        OPERATION_TYPE.key, transactionType.name(),
        PARTY_CODE.key, party.code(),
        PARTY_TYPE.key, party.type().name());
  }

  private LedgerParty getParty(PartyId party) {
    var partyType = PartyType.valueOf(party.type().name());
    return ledgerPartyService.getOrCreate(party.code(), partyType);
  }

  private LedgerAccount getUserAccount(LedgerParty owner, UserAccount userAccount) {
    return ledgerAccountService
        .findUserAccount(owner, userAccount)
        .orElseGet(() -> ledgerAccountService.createUserAccount(owner, userAccount));
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount, TKF100)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount, TKF100));
  }

  private LedgerAccount getUserCashAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH);
  }

  private LedgerAccount getUserCashReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_RESERVED);
  }

  private LedgerAccount getUserCashRedemptionAccount(LedgerParty owner) {
    return getUserAccount(owner, CASH_REDEMPTION);
  }

  private LedgerAccount getUserUnitsAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS);
  }

  private LedgerAccount getUserUnitsReservedAccount(LedgerParty owner) {
    return getUserAccount(owner, FUND_UNITS_RESERVED);
  }

  private LedgerAccount getUserSubscriptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, SUBSCRIPTIONS);
  }

  private LedgerAccount getUserRedemptionsAccount(LedgerParty owner) {
    return getUserAccount(owner, REDEMPTIONS);
  }

  private LedgerAccount getIncomingPaymentsClearingAccount() {
    return getSystemAccount(INCOMING_PAYMENTS_CLEARING);
  }

  private LedgerAccount getUnreconciledBankReceiptsAccount() {
    return getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
  }

  private LedgerAccount getFundInvestmentCashClearingAccount() {
    return getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);
  }

  private LedgerAccount getFundUnitsOutstandingAccount() {
    return getSystemAccount(FUND_UNITS_OUTSTANDING);
  }

  private LedgerAccount getPayoutsCashClearingAccount() {
    return getSystemAccount(PAYOUTS_CASH_CLEARING);
  }

  public boolean hasLedgerEntry(UUID externalReference, TransactionType transactionType) {
    return ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, transactionType);
  }

  public boolean hasPricingEntry(UUID redemptionRequestId) {
    return hasLedgerEntry(redemptionRequestId, REDEMPTION_REQUEST);
  }

  public boolean hasPayoutEntry(UUID redemptionRequestId) {
    return hasLedgerEntry(redemptionRequestId, REDEMPTION_PAYOUT);
  }

  @Transactional
  public LedgerTransaction recordTradeSettlement(
      BigDecimal amount,
      BigDecimal units,
      UUID externalReference,
      SystemAccount clearingAccount,
      String isin,
      String ticker,
      String displayName) {
    return recordTradeSettlement(
        amount,
        units,
        externalReference,
        clearingAccount,
        isin,
        ticker,
        displayName,
        LocalDate.now(clock));
  }

  @Transactional
  public LedgerTransaction recordTradeSettlement(
      BigDecimal amount,
      BigDecimal units,
      UUID externalReference,
      SystemAccount clearingAccount,
      String isin,
      String ticker,
      String displayName,
      LocalDate bookingDate) {
    LedgerAccount clearingLedgerAccount = getSystemAccount(clearingAccount);
    LedgerAccount tradeSettlementAccount =
        findOrCreateInstrumentAccount(
            TRADE_CASH_SETTLEMENT, TRADE_CASH_SETTLEMENT.getAccountName(TKF100, isin));
    LedgerAccount securityUnitsAccount =
        findOrCreateInstrumentAccount(
            TRADE_UNIT_SETTLEMENT, TRADE_UNIT_SETTLEMENT.getAccountName(TKF100, isin));
    LedgerAccount securitiesCustodyAccount =
        findOrCreateInstrumentAccount(
            SECURITIES_CUSTODY, SECURITIES_CUSTODY.getAccountName(TKF100, isin));

    Map<String, Object> metadata =
        Map.of(
            OPERATION_TYPE.key, TRADE_SETTLEMENT.name(),
            INSTRUMENT.key, isin,
            TICKER.key, ticker,
            DISPLAY_NAME.key, displayName);

    return ledgerTransactionService.createTransaction(
        TRADE_SETTLEMENT,
        transactionDate(bookingDate),
        externalReference,
        metadata,
        entry(clearingLedgerAccount, amount),
        entry(tradeSettlementAccount, amount.negate()),
        entry(securityUnitsAccount, units.negate()),
        entry(securitiesCustodyAccount, units));
  }
}
