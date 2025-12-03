package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SwedbankBankStatementMessageProcessor {

  private final SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  private final SavingFundPaymentExtractor paymentExtractor;
  private final SavingFundPaymentUpsertionService paymentService;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;
  private final SavingsFundLedger savingsFundLedger;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final UserRepository userRepository;

  @Transactional
  public void processMessage(String rawResponse, SwedbankMessageType messageType) {
    log.info("Processing bank statement message of type {}", messageType);

    var bankStatement =
        switch (messageType) {
          case INTRA_DAY_REPORT ->
              swedbankBankStatementExtractor.extractFromIntraDayReport(rawResponse);
          case HISTORIC_STATEMENT ->
              swedbankBankStatementExtractor.extractFromHistoricStatement(rawResponse);
          case PAYMENT_ORDER_CONFIRMATION ->
              throw new IllegalArgumentException("Message type not supported: " + messageType);
        };
    log.info(
        "Successfully extracted bank statement message: entries={}",
        bankStatement.getEntries().size());

    var accountIban = bankStatement.getBankStatementAccount().iban();
    if (swedbankAccountConfiguration.getAccountType(accountIban) != DEPOSIT_EUR) {
      log.info(
          "Skipping payment processing as it is not a DEPOSIT_EUR account: account={}",
          accountIban);
      return;
    }

    var payments = paymentExtractor.extractPayments(bankStatement);

    log.info("Successfully extracted {} payments from a bank statement", payments.size());

    payments.forEach(payment -> paymentService.upsert(payment, this::handleInsertedPayment));

    log.info("Successfully upserted {} payments", payments.size());
  }

  private void handleInsertedPayment(SavingFundPayment payment) {
    if (isOutgoingToFundAccount(payment)) {
      log.info(
          "Creating ledger entry for transfer to fund investment account: amount={}",
          payment.getAmount().negate());
      savingsFundLedger.transferToFundAccount(payment.getAmount().negate());
    } else if (isOutgoingReturn(payment)) {
      findOriginalPaymentForReturn(payment)
          .ifPresentOrElse(
              originalPayment -> completeUserCancelledPaymentReturn(originalPayment, payment),
              () -> completeUnattributedPaymentBounceBack(payment));
    }
  }

  private void completeUserCancelledPaymentReturn(
      SavingFundPayment originalPayment, SavingFundPayment returnPayment) {
    userRepository
        .findById(originalPayment.getUserId())
        .ifPresentOrElse(
            user -> {
              log.info(
                  "Completing ledger entry for user-cancelled payment: paymentId={}, amount={}, to={}",
                  originalPayment.getId(),
                  returnPayment.getAmount().negate(),
                  returnPayment.getBeneficiaryIban());
              savingsFundLedger.recordPaymentCancelled(
                  user, returnPayment.getAmount().negate(), originalPayment.getId());
            },
            () ->
                log.warn(
                    "User not found for cancelled payment return: userId={}, paymentId={}",
                    originalPayment.getUserId(),
                    originalPayment.getId()));
  }

  private void completeUnattributedPaymentBounceBack(SavingFundPayment payment) {
    log.info(
        "Creating ledger entry for unattributed payment bounce back: paymentId={}, amount={}, to={}",
        payment.getId(),
        payment.getAmount().negate(),
        payment.getBeneficiaryIban());
    savingsFundLedger.bounceBackUnattributedPayment(payment.getAmount().negate(), payment.getId());
  }

  private Optional<SavingFundPayment> findOriginalPaymentForReturn(
      SavingFundPayment returnPayment) {
    return savingFundPaymentRepository
        .findReturnedPaymentByRemitterIbanAndAmount(
            returnPayment.getBeneficiaryIban(), returnPayment.getAmount().negate())
        .filter(originalPayment -> originalPayment.getUserId() != null);
  }

  private boolean isOutgoingToFundAccount(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && swedbankAccountConfiguration.getAccountType(payment.getBeneficiaryIban())
            == FUND_INVESTMENT_EUR;
  }

  private boolean isOutgoingReturn(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && swedbankAccountConfiguration.getAccountType(payment.getBeneficiaryIban())
            != FUND_INVESTMENT_EUR;
  }
}
