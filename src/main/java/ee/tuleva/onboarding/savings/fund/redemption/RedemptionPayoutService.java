package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PAYOUT_HELD;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.BankAccounts;
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
  private final TransactionTemplate transactionTemplate;

  void payOut(RedemptionRequest request) {
    sendPayout(request);
    markAsRedeemed(request.getId());
  }

  // The claim (PAYOUT_HELD -> REDEEMED under a row lock) commits before the bank is called, so two
  // concurrent releases cannot both send the money; a failed send is recorded as FAILED for retry.
  void payOutHeld(UUID requestId) {
    RedemptionRequest claimed =
        requireNonNull(transactionTemplate.execute(tx -> claimHeldPayout(requestId)));
    try {
      sendPayout(claimed);
    } catch (Exception e) {
      log.error("Failed to pay out released redemption: id={}", requestId, e);
      markAsFailed(requestId, e);
    }
  }

  private RedemptionRequest claimHeldPayout(UUID requestId) {
    RedemptionRequest request =
        redemptionRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(
                () -> new NoSuchElementException("Redemption request not found: id=" + requestId));
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
    markAsRedeemed(requestId);
    return request;
  }

  void sendPayout(RedemptionRequest request) {
    BigDecimal cashAmount = request.getCashAmount();
    if (cashAmount == null) {
      throw new IllegalStateException("Cannot pay out, not priced: id=" + request.getId());
    }
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
    eventPublisher.publishEvent(new RequestPaymentEvent(paymentRequest, request.getId()));
    log.info("Sent redemption payout: id={}, amount={}", request.getId(), cashAmount);
  }

  void markAsRedeemed(UUID requestId) {
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
      redemptionStatusService.changeStatus(requestId, FAILED);
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
