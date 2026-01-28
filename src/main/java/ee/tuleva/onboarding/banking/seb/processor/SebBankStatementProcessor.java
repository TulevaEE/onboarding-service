package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.processor.BankOperationProcessor;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionStatusService;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SebBankStatementProcessor {

  private final SavingFundPaymentExtractor paymentExtractor;
  private final SavingFundPaymentUpsertionService paymentService;
  private final SebAccountConfiguration sebAccountConfiguration;
  private final SavingsFundLedger savingsFundLedger;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserService userService;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final EndToEndIdConverter endToEndIdConverter;
  private final BankOperationProcessor bankOperationProcessor;

  public void processStatement(BankStatement bankStatement, UUID messageId) {
    log.info(
        "Processing bank statement: type={}, entries={}",
        bankStatement.getType(),
        bankStatement.getEntries().size());

    var accountIban = bankStatement.getBankStatementAccount().iban();
    var accountType = sebAccountConfiguration.getAccountType(accountIban);

    if (accountType == null) {
      log.warn("Unknown account type: iban={}", accountIban);
      return;
    }

    var payments = paymentExtractor.extractPayments(bankStatement);
    log.info("Extracted payments: count={}", payments.size());

    payments.forEach(payment -> processPayment(payment, accountType));
    log.info("Processed payments: count={}, accountType={}", payments.size(), accountType);

    bankStatement.getEntries().stream()
        .filter(entry -> entry.details() == null)
        .forEach(entry -> bankOperationProcessor.processBankOperation(entry, messageId));
  }

  private SavingFundPayment.Status resolveDepositAccountStatus(SavingFundPayment payment) {
    return isIncomingPayment(payment)
        ? SavingFundPayment.Status.RECEIVED
        : SavingFundPayment.Status.PROCESSED;
  }

  private SavingFundPayment.Status processDepositPaymentOnInsert(SavingFundPayment payment) {
    handleDepositAccountPayment(payment);
    return resolveDepositAccountStatus(payment);
  }

  private SavingFundPayment.Status processWithdrawalPaymentOnInsert(SavingFundPayment payment) {
    handleWithdrawalAccountPayment(payment);
    return SavingFundPayment.Status.PROCESSED;
  }

  private SavingFundPayment.Status processFundInvestmentPaymentOnInsert(SavingFundPayment payment) {
    handleFundInvestmentAccountPayment(payment);
    return SavingFundPayment.Status.PROCESSED;
  }

  private void processPayment(SavingFundPayment payment, BankAccountType accountType) {
    switch (accountType) {
      case DEPOSIT_EUR ->
          paymentService.upsert(
              payment, this::processDepositPaymentOnInsert, this::resolveDepositAccountStatus);
      case WITHDRAWAL_EUR -> paymentService.upsert(payment, this::processWithdrawalPaymentOnInsert);
      case FUND_INVESTMENT_EUR ->
          paymentService.upsert(payment, this::processFundInvestmentPaymentOnInsert);
    }
  }

  private void handleDepositAccountPayment(SavingFundPayment payment) {
    if (isOutgoingToFundAccount(payment)) {
      log.info(
          "Creating ledger entry for transfer to fund investment account: amount={}",
          payment.getAmount().negate());
      savingsFundLedger.transferToFundAccount(payment.getAmount().negate());
    } else if (isOutgoingReturn(payment)) {
      findOriginalPaymentForReturn(payment)
          .ifPresentOrElse(
              this::completePaymentReturn,
              () ->
                  log.error(
                      "Original payment not found for return: endToEndId={}, beneficiaryIban={}, amount={}",
                      payment.getEndToEndId(),
                      payment.getBeneficiaryIban(),
                      payment.getAmount()));
    } else if (isIncomingPayment(payment)) {
      log.debug(
          "Incoming payment inserted, ledger entry handled by verification: paymentId={}",
          payment.getId());
    } else {
      log.error(
          "Unhandled payment type: paymentId={}, amount={}", payment.getId(), payment.getAmount());
    }
  }

  private boolean isIncomingPayment(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) > 0;
  }

  private boolean isOutgoingPayment(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0;
  }

  private void handleWithdrawalAccountPayment(SavingFundPayment payment) {
    if (isOutgoingPayment(payment)) {
      handleOutgoingRedemptionPayout(payment);
    } else if (isIncomingFromFundInvestment(payment)) {
      log.info(
          "Batch transfer received in WITHDRAWAL_EUR from FUND_INVESTMENT_EUR: amount={}",
          payment.getAmount());
    } else {
      log.error(
          "Unhandled WITHDRAWAL_EUR payment: amount={}, remitterIban={}",
          payment.getAmount(),
          payment.getRemitterIban());
    }
  }

  private void handleOutgoingRedemptionPayout(SavingFundPayment payment) {
    findRedemptionRequestByEndToEndId(payment.getEndToEndId())
        .ifPresentOrElse(
            request -> processRedemptionPayout(request, payment),
            () ->
                log.error(
                    "No matching RedemptionRequest found for outgoing payment: endToEndId={}, beneficiaryIban={}, amount={}",
                    payment.getEndToEndId(),
                    payment.getBeneficiaryIban(),
                    payment.getAmount()));
  }

  private void processRedemptionPayout(RedemptionRequest request, SavingFundPayment payment) {
    if (savingsFundLedger.hasPayoutEntry(request.getId())) {
      log.error(
          "Ledger payout entry already exists but status is REDEEMED: id={}", request.getId());
    } else {
      var user = userService.getByIdOrThrow(request.getUserId());
      var amount = payment.getAmount().negate();
      log.info(
          "Creating ledger entry for redemption payout: redemptionId={}, amount={}",
          request.getId(),
          amount);
      savingsFundLedger.recordRedemptionPayout(
          user, amount, request.getCustomerIban(), request.getId());
    }
    markRedemptionAsProcessed(request);
  }

  private Optional<RedemptionRequest> findRedemptionRequestByEndToEndId(String endToEndId) {
    return endToEndIdConverter
        .toUuid(endToEndId)
        .flatMap(
            id ->
                redemptionRequestRepository.findByIdAndStatus(
                    id, RedemptionRequest.Status.REDEEMED));
  }

  private void markRedemptionAsProcessed(RedemptionRequest request) {
    log.info("Marking redemption as PROCESSED: id={}", request.getId());
    redemptionStatusService.changeStatus(request.getId(), RedemptionRequest.Status.PROCESSED);
  }

  private boolean isIncomingFromFundInvestment(SavingFundPayment payment) {
    return isIncomingPayment(payment)
        && sebAccountConfiguration.getAccountType(payment.getRemitterIban()) == FUND_INVESTMENT_EUR;
  }

  private void handleFundInvestmentAccountPayment(SavingFundPayment payment) {
    if (isOutgoingToWithdrawalAccount(payment)) {
      var amount = payment.getAmount().negate();
      log.info("Creating ledger entry for batch transfer to withdrawal account: amount={}", amount);
      savingsFundLedger.transferFromFundAccount(amount);
    } else {
      log.debug(
          "FUND_INVESTMENT_EUR payment: amount={}, beneficiaryIban={}",
          payment.getAmount(),
          payment.getBeneficiaryIban());
    }
  }

  private boolean isOutgoingToWithdrawalAccount(SavingFundPayment payment) {
    return isOutgoingPayment(payment)
        && sebAccountConfiguration.getAccountType(payment.getBeneficiaryIban()) == WITHDRAWAL_EUR;
  }

  private void completePaymentReturn(SavingFundPayment originalPayment) {
    if (isUserCancelledPayment(originalPayment)) {
      completeUserCancelledPaymentReturn(originalPayment);
    } else {
      completeUnattributedPaymentBounceBack(originalPayment);
    }
  }

  private boolean isUserCancelledPayment(SavingFundPayment payment) {
    return payment.getUserId() != null;
  }

  private void completeUserCancelledPaymentReturn(SavingFundPayment originalPayment) {
    var user = userService.getByIdOrThrow(originalPayment.getUserId());
    log.info(
        "Completing ledger entry for user-cancelled payment: paymentId={}, amount={}",
        originalPayment.getId(),
        originalPayment.getAmount());
    savingsFundLedger.recordPaymentCancelled(
        user, originalPayment.getAmount(), originalPayment.getId());
  }

  private void completeUnattributedPaymentBounceBack(SavingFundPayment originalPayment) {
    log.info(
        "Completing ledger entry for unattributed payment bounce back: paymentId={}, amount={}",
        originalPayment.getId(),
        originalPayment.getAmount());
    savingsFundLedger.bounceBackUnattributedPayment(
        originalPayment.getAmount(), originalPayment.getId());
  }

  private Optional<SavingFundPayment> findOriginalPaymentForReturn(
      SavingFundPayment returnPayment) {
    return savingFundPaymentRepository.findOriginalPaymentForReturn(returnPayment.getEndToEndId());
  }

  private boolean isOutgoingToFundAccount(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && sebAccountConfiguration.getAccountType(payment.getBeneficiaryIban())
            == FUND_INVESTMENT_EUR;
  }

  private boolean isOutgoingReturn(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && sebAccountConfiguration.getAccountType(payment.getBeneficiaryIban())
            != FUND_INVESTMENT_EUR;
  }
}
