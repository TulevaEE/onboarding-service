package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ONBOARDING_INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_MATCH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.aml.ScreeningOutcome;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionVerificationService {

  private final RedemptionStatusService redemptionStatusService;
  private final UserService userService;
  private final KycCountryService kycCountryService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final RiskLevels riskLevels;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final OperationsNotificationService notificationService;
  private final SavingFundDeadlinesService deadlinesService;
  private final Clock clock;

  @Transactional
  public void process(RedemptionRequest request) {
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());
    Optional<RedemptionHoldReason> holdReason =
        switch (request.getPartyId().type()) {
          case PERSON -> verifyPerson(request);
          case LEGAL_ENTITY -> verifyLegalEntity(request);
        };
    if (holdReason.isEmpty()) {
      log.info(
          "Redemption verification passed: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    } else if (holdReason.get() == SCREENING_UNAVAILABLE && canStillRetry(request)) {
      log.info(
          "Screening unavailable, retrying until deadline: id={}, party={}, deadline={}",
          request.getId(),
          request.getPartyId(),
          deadlinesService.getScreeningRetryDeadline(request));
    } else {
      hold(request, holdReason.get());
    }
  }

  private boolean canStillRetry(RedemptionRequest request) {
    return Instant.now(clock).isBefore(deadlinesService.getScreeningRetryDeadline(request));
  }

  private void hold(RedemptionRequest request, RedemptionHoldReason reason) {
    log.info(
        "Redemption requires review: id={}, party={}, reason={}",
        request.getId(),
        request.getPartyId(),
        reason);
    redemptionStatusService.holdForReview(request.getId(), reason);
    notifyAmlChannel(request, reason);
  }

  private void notifyAmlChannel(RedemptionRequest request, RedemptionHoldReason reason) {
    try {
      notificationService.sendMessage(
          "AML: redemption held for review: id=%s, amount=%s EUR, reason=%s"
              .formatted(request.getId(), request.getRequestedAmount().toPlainString(), reason),
          AML);
    } catch (RuntimeException e) {
      log.error("Failed to notify AML channel about held redemption: id={}", request.getId(), e);
    }
  }

  private Optional<RedemptionHoldReason> verifyPerson(RedemptionRequest request) {
    User user =
        userService
            .findByPersonalCode(request.getPartyId().code())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Redemption party user not found: party=" + request.getPartyId()));
    Set<Country> countries =
        kycCountryService
            .getCountries(user.getIdOrThrow())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "KYC survey with country not found: userId=" + user.getIdOrThrow()));
    Set<Country> allCountries = new HashSet<>(countries);
    allCountries.addAll(sanctionAndPepScreener.recordedCitizenships(user));

    ScreeningOutcome screening = sanctionAndPepScreener.screeningOutcome(user, allCountries);
    boolean highRisk = riskLevels.isHighRisk(user.getPersonalCode());
    if (highRisk) {
      log.info(
          "Redemption party is high risk: id={}, party={}", request.getId(), request.getPartyId());
    }
    return switch (screening) {
      case MATCH -> Optional.of(SCREENING_MATCH);
      case UNAVAILABLE -> Optional.of(highRisk ? HIGH_RISK : SCREENING_UNAVAILABLE);
      case CLEAR -> highRisk ? Optional.of(HIGH_RISK) : Optional.empty();
    };
  }

  private Optional<RedemptionHoldReason> verifyLegalEntity(RedemptionRequest request) {
    var registryCode = request.getPartyId().code();
    if (savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)) {
      return Optional.empty();
    }
    var needsScreening =
        savingsFundOnboardingRepository
            .findStatus(registryCode, LEGAL_ENTITY)
            .map(status -> status == PENDING)
            .orElse(true);
    if (!needsScreening) {
      return Optional.of(ONBOARDING_INCOMPLETE);
    }
    try {
      legalEntityScreener.screenLatest(registryCode);
    } catch (RuntimeException e) {
      log.error(
          "Failed to re-screen legal entity for redemption: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return Optional.of(SCREENING_UNAVAILABLE);
    }
    return savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)
        ? Optional.empty()
        : Optional.of(ONBOARDING_INCOMPLETE);
  }
}
