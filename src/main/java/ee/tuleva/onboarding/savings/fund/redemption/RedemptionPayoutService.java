package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_BLOCKED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PAYOUT_HELD;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
class RedemptionPayoutService {

  private final Clock clock;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final ApplicationEventPublisher eventPublisher;
  private final BankAccounts bankAccounts;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final EndToEndIdConverter endToEndIdConverter;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final RedemptionPayoutValidator payoutValidator;
  private final PaymentCheckService paymentCheckService;
  private final TransactionTemplate transactionTemplate;

  enum Outcome {
    PAID,
    HELD,
    SKIPPED,
    FAILED_TO_SEND
  }

  // Claiming (VERIFIED -> REDEEMED under a row lock) commits before the bank is called, so a hold
  // or a release landing mid-batch cannot leave the money sent twice or sent after a hold.
  Outcome payOut(UUID requestId, @Nullable UUID batchId) {
    Claim claim;
    try {
      claim = requireNonNull(transactionTemplate.execute(tx -> claimForPayout(requestId)));
    } catch (Exception e) {
      log.error("Failed to claim redemption for payout: id={}", requestId, e);
      markAsFailed(requestId, e);
      return Outcome.FAILED_TO_SEND;
    }
    RedemptionRequest claimed = claim.request();
    return claim.outcome() == Outcome.PAID && claimed != null
        ? send(claimed, batchId)
        : claim.outcome();
  }

  // The hold has just been released, so the claim only has to win the race against a second
  // release and against the batch job picking the same request up.
  Outcome payOutHeld(UUID requestId) {
    RedemptionRequest claimed =
        requireNonNull(transactionTemplate.execute(tx -> claimHeldPayout(requestId)));
    return send(claimed, null);
  }

  // An admin retry already holds the row lock and has checked the request, and a failure should
  // surface to the caller rather than being swallowed into another FAILED.
  void payOutOnRetry(RedemptionRequest request) {
    markAsRedeemed(request.getId());
    sendPayout(request, null);
  }

  private Outcome send(RedemptionRequest claimed, @Nullable UUID batchId) {
    try {
      sendPayout(claimed, batchId);
      return Outcome.PAID;
    } catch (Exception e) {
      log.error("Failed to send redemption payout: id={}", claimed.getId(), e);
      markAsFailed(claimed.getId(), e);
      return Outcome.FAILED_TO_SEND;
    }
  }

  private Claim claimForPayout(UUID requestId) {
    RedemptionRequest request = lockOrThrow(requestId);
    if (request.getStatus() != VERIFIED || request.getCashAmount() == null) {
      log.info(
          "Redemption is no longer payable, skipping: id={}, status={}",
          requestId,
          request.getStatus());
      return new Claim(Outcome.SKIPPED, null);
    }
    if (request.hasActiveHold()) {
      redemptionStatusService.changeStatus(requestId, PAYOUT_HELD);
      log.info(
          "Held payout of redemption under AML review: id={}, cashAmount={}, reasons={}",
          requestId,
          request.getCashAmount(),
          request.getHoldReasons());
      return new Claim(Outcome.HELD, request);
    }
    requirePayable(request);
    markAsRedeemed(requestId);
    return new Claim(Outcome.PAID, request);
  }

  // A claim carries the request only when it is the caller's to send.
  private record Claim(Outcome outcome, @Nullable RedemptionRequest request) {}

  private RedemptionRequest claimHeldPayout(UUID requestId) {
    RedemptionRequest request = lockOrThrow(requestId);
    if (request.getStatus() != PAYOUT_HELD) {
      throw new IllegalStateException(
          "Cannot pay out, payout is not held: id="
              + requestId
              + ", status="
              + request.getStatus());
    }
    if (request.getCashAmount() == null) {
      throw new IllegalStateException("Cannot pay out, not priced: id=" + requestId);
    }
    requirePayable(request);
    markAsRedeemed(requestId);
    return request;
  }

  // The IBAN and the ledger can have moved while the money sat on hold, so the payout
  // preconditions are checked again here, not only before pricing.
  private void requirePayable(RedemptionRequest request) {
    payoutValidator
        .findBlockingReason(request)
        .ifPresent(
            reason -> {
              paymentCheckService.recordStoppedPayment(
                  PAYOUT_BLOCKED, request.getId().toString(), reason);
              throw new IllegalStateException(
                  "Cannot pay out redemption: id=" + request.getId() + ", reason=" + reason);
            });
  }

  private RedemptionRequest lockOrThrow(UUID requestId) {
    return redemptionRequestRepository
        .findByIdForUpdate(requestId)
        .orElseThrow(
            () -> new NoSuchElementException("Redemption request not found: id=" + requestId));
  }

  private void sendPayout(RedemptionRequest request, @Nullable UUID batchId) {
    BigDecimal cashAmount = requireNonNull(request.getCashAmount());
    PartyId party = request.getPartyId();
    String beneficiaryName = getBeneficiaryName(party, request.getCustomerIban());
    PaymentRequest paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(endToEndIdConverter.toEndToEndId(request.getId()))
            .remitterIban(bankAccounts.getIban(TKF100, WITHDRAWAL_EUR))
            .beneficiaryName(beneficiaryName)
            .beneficiaryIban(request.getCustomerIban())
            .amount(cashAmount)
            .description("Fondi tagasivõtmine")
            .build();
    eventPublisher.publishEvent(
        new RequestPaymentEvent(paymentRequest, request.getId(), PAYOUT, batchId));
    log.info(
        "Sent redemption payout: id={}, amount={}, iban={}, beneficiaryName={}",
        request.getId(),
        cashAmount,
        request.getCustomerIban(),
        beneficiaryName);
  }

  private void markAsRedeemed(UUID requestId) {
    redemptionStatusService.changeStatus(requestId, REDEEMED);
    RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElseThrow();
    request.setProcessedAt(Instant.now(clock));
    redemptionRequestRepository.save(request);
  }

  void markAsFailed(UUID requestId, Exception e) {
    try {
      RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElseThrow();
      request.setErrorReason(e.toString());
      redemptionRequestRepository.save(request);
      if (request.getStatus() != FAILED) {
        redemptionStatusService.changeStatus(requestId, FAILED);
      }
    } catch (Exception ex) {
      log.error("Failed to mark redemption as failed: id={}", requestId, ex);
    }
  }

  private String getBeneficiaryName(PartyId partyId, String iban) {
    return savingFundPaymentRepository
        .findRemitterNameByIban(partyId, iban)
        .or(() -> registeredPartyName(partyId))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Beneficiary name not resolvable: party=" + partyId + ", iban=" + iban));
  }

  // Defensive: a deposit normally yields a remitter name, but a whitelisted IBAN may have no
  // deposit row at all, and a deposit's bank statement may carry a null remitter_name. Fall back
  // to the party's registered name rather than failing the payout.
  private Optional<String> registeredPartyName(PartyId partyId) {
    return switch (partyId.type()) {
      case LEGAL_ENTITY ->
          companyRepository.findByRegistryCode(partyId.code()).map(Company::getName);
      case PERSON -> userRepository.findByPersonalCode(partyId.code()).map(User::getFullName);
    };
  }
}
