package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.ManagementCompanies;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.SavingsFundStatementReceived;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionStatusService;
import ee.tuleva.onboarding.user.UserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "seb-gateway", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SavingsFundStatementProcessor {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final SavingFundPaymentExtractor paymentExtractor;
  private final SavingFundPaymentUpsertionService paymentService;
  private final ManagementCompanies managementCompanies;
  private final BankAccounts bankAccounts;
  private final SavingsFundLedger savingsFundLedger;
  private final FundBankLedger fundBankLedger;
  private final UserService userService;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final EndToEndIdConverter endToEndIdConverter;

  @EventListener
  public void onStatementReceived(SavingsFundStatementReceived event) {
    process(event.statement(), event.account());
  }

  public void process(BankStatement bankStatement, BankAccount account) {
    log.info(
        "Processing bank statement: type={}, entries={}",
        bankStatement.getType(),
        bankStatement.getEntries().size());

    var payments = paymentExtractor.extractPayments(bankStatement);
    log.info("Extracted payments: count={}", payments.size());

    payments.forEach(payment -> processPayment(payment, account.type()));
    log.info("Processed payments: count={}, account={}", payments.size(), account);
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
    if (isInternalTransferIncoming(payment)) {
      log.debug(
          "Skipping incoming internal transfer: endToEndId={}, remitterIban={}",
          payment.getEndToEndId(),
          payment.getRemitterIban());
      return;
    }

    switch (accountType) {
      case DEPOSIT_EUR ->
          paymentService.upsert(
              payment, this::processDepositPaymentOnInsert, this::resolveDepositAccountStatus);
      case WITHDRAWAL_EUR -> paymentService.upsert(payment, this::processWithdrawalPaymentOnInsert);
      case FUND_INVESTMENT_EUR ->
          paymentService.upsert(payment, this::processFundInvestmentPaymentOnInsert);
    }
  }

  private boolean isInternalTransferIncoming(SavingFundPayment payment) {
    return isIncomingPayment(payment) && isSavingsFundAccount(payment.getRemitterIban());
  }

  private void handleDepositAccountPayment(SavingFundPayment payment) {
    if (isOutgoingToFundAccount(payment)) {
      log.info(
          "Creating ledger entry for transfer to fund investment account: amount={}",
          payment.getAmount().negate());
      savingsFundLedger.transferToFundAccount(
          payment.getAmount().negate(), payment.getId(), bookingDate(payment));
    } else if (isOutgoingReturn(payment)) {
      log.info(
          "Outgoing return detected, deferring matching to post-processing pass: endToEndId={}, beneficiaryIban={}, amount={}",
          payment.getEndToEndId(),
          payment.getBeneficiaryIban(),
          payment.getAmount());
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
      var party = new PartyId(PartyId.Type.PERSON, user.getPersonalCode());
      var amount = payment.getAmount().negate();
      log.info(
          "Creating ledger entry for redemption payout: redemptionId={}, amount={}",
          request.getId(),
          amount);
      savingsFundLedger.recordRedemptionPayout(
          party, amount, request.getCustomerIban(), request.getId(), bookingDate(payment));
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
        && isSavingsFundAccount(payment.getRemitterIban(), FUND_INVESTMENT_EUR);
  }

  private void handleFundInvestmentAccountPayment(SavingFundPayment payment) {
    if (isOutgoingToWithdrawalAccount(payment)) {
      var amount = payment.getAmount().negate();
      log.info("Creating ledger entry for batch transfer to withdrawal account: amount={}", amount);
      savingsFundLedger.transferFromFundAccount(amount, payment.getId(), bookingDate(payment));
    } else if (isManagementFeePayment(payment)) {
      var amount = payment.getAmount().negate();
      log.info("Creating ledger entry for management fee payment: amount={}", amount);
      fundBankLedger.recordManagementFeePayment(
          TKF100, amount, payment.getId(), payment.getDescription(), bookingDate(payment));
    } else {
      log.error(
          "Unhandled FUND_INVESTMENT_EUR payment: paymentId={}, amount={}, beneficiaryIban={}",
          payment.getId(),
          payment.getAmount(),
          payment.getBeneficiaryIban());
    }
  }

  private boolean isManagementFeePayment(SavingFundPayment payment) {
    return isOutgoingPayment(payment)
        && managementCompanies.isManagementCompany(payment.getBeneficiaryName())
        && payment.getDescription() != null
        && payment.getDescription().toLowerCase().contains("valitsemistasu");
  }

  private boolean isOutgoingToWithdrawalAccount(SavingFundPayment payment) {
    return isOutgoingPayment(payment)
        && isSavingsFundAccount(payment.getBeneficiaryIban(), WITHDRAWAL_EUR);
  }

  private boolean isOutgoingToFundAccount(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && isSavingsFundAccount(payment.getBeneficiaryIban(), FUND_INVESTMENT_EUR);
  }

  private boolean isOutgoingReturn(SavingFundPayment payment) {
    return payment.getAmount().compareTo(ZERO) < 0
        && !isSavingsFundAccount(payment.getBeneficiaryIban(), FUND_INVESTMENT_EUR);
  }

  private boolean isSavingsFundAccount(String iban) {
    return bankAccounts.find(iban).filter(account -> account.belongsTo(TKF100)).isPresent();
  }

  private boolean isSavingsFundAccount(String iban, BankAccountType type) {
    return bankAccounts.find(iban).filter(account -> account.matches(TKF100, type)).isPresent();
  }

  private static LocalDate bookingDate(SavingFundPayment payment) {
    return payment.getReceivedBefore().atZone(ESTONIAN_ZONE).toLocalDate();
  }
}
