package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.KYB_SCREENING_FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ONBOARDING_INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.aml.ScreeningOutcome;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.ws.client.WebServiceIOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionVerificationService {

  private static final Duration SCREENING_RETRY_BACKOFF = Duration.ofMinutes(5);

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final RedemptionHoldService holdService;
  private final UserService userService;
  private final KycCountryService kycCountryService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final RiskLevels riskLevels;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final SavingFundDeadlinesService deadlinesService;
  private final Clock clock;

  @Transactional
  public void process(RedemptionRequest request) {
    if (attemptedWithinBackoff(request)) {
      return;
    }
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());

    Verdict verdict =
        switch (request.getPartyId().type()) {
          case PERSON -> verifyPerson(request);
          case LEGAL_ENTITY -> verifyLegalEntity(request);
        };

    switch (verdict) {
      case Verdict.RetryLater ignored -> retryLater(request);
      case Verdict.Freeze ignored -> holdService.freeze(request.getId());
      case Verdict.HoldPayout hold -> {
        holdService.holdPayout(request.getId(), hold.reasons());
        redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      }
      case Verdict.Clear ignored -> {
        log.info(
            "Redemption verification passed: id={}, party={}",
            request.getId(),
            request.getPartyId());
        redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      }
    }
  }

  // An outage is not a suspicion, so it must not cost the saver their dealing date: retry with a
  // backoff, and once the deadline passes execute the order and hold the cash instead.
  private void retryLater(RedemptionRequest request) {
    if (!canStillRetry(request)) {
      holdService.holdPayout(request.getId(), Set.of(SCREENING_UNAVAILABLE));
      redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      return;
    }
    request.setVerificationAttemptedAt(Instant.now(clock));
    redemptionRequestRepository.save(request);
    log.info(
        "Screening unavailable, retrying until deadline: id={}, party={}, deadline={}",
        request.getId(),
        request.getPartyId(),
        deadlinesService.getScreeningRetryDeadline(request));
  }

  private boolean attemptedWithinBackoff(RedemptionRequest request) {
    Instant attemptedAt = request.getVerificationAttemptedAt();
    return attemptedAt != null
        && attemptedAt.isAfter(Instant.now(clock).minus(SCREENING_RETRY_BACKOFF));
  }

  private boolean canStillRetry(RedemptionRequest request) {
    return Instant.now(clock).isBefore(deadlinesService.getScreeningRetryDeadline(request));
  }

  private Verdict verifyPerson(RedemptionRequest request) {
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
    if (screening == ScreeningOutcome.UNAVAILABLE) {
      return new Verdict.RetryLater();
    }
    if (screening == ScreeningOutcome.SANCTION_HIT) {
      return new Verdict.Freeze();
    }
    Set<RedemptionHoldReason> reasons = EnumSet.noneOf(RedemptionHoldReason.class);
    if (screening == ScreeningOutcome.PEP_HIT) {
      reasons.add(PEP);
    }
    if (riskLevels.isHighRisk(user.getPersonalCode())) {
      log.info(
          "Redemption party is high risk: id={}, party={}", request.getId(), request.getPartyId());
      reasons.add(HIGH_RISK);
    }
    return reasons.isEmpty() ? new Verdict.Clear() : new Verdict.HoldPayout(reasons);
  }

  private Verdict verifyLegalEntity(RedemptionRequest request) {
    var registryCode = request.getPartyId().code();
    if (savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)) {
      return new Verdict.Clear();
    }
    var needsScreening =
        savingsFundOnboardingRepository
            .findStatus(registryCode, LEGAL_ENTITY)
            .map(status -> status == PENDING)
            .orElse(true);
    if (!needsScreening) {
      return new Verdict.HoldPayout(Set.of(ONBOARDING_INCOMPLETE));
    }
    List<KybCheck> checks;
    try {
      checks = legalEntityScreener.screenLatest(registryCode);
    } catch (WebServiceIOException | RestClientException e) {
      log.error(
          "Legal entity screening service unavailable: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return new Verdict.RetryLater();
    } catch (RuntimeException e) {
      log.error(
          "Failed to re-screen legal entity for redemption: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return new Verdict.HoldPayout(Set.of(KYB_SCREENING_FAILED));
    }
    if (checks.stream().anyMatch(check -> check.type() == COMPANY_SANCTION && !check.success())) {
      return new Verdict.Freeze();
    }
    return savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)
        ? new Verdict.Clear()
        : new Verdict.HoldPayout(Set.of(ONBOARDING_INCOMPLETE));
  }

  private sealed interface Verdict {
    record RetryLater() implements Verdict {}

    record Freeze() implements Verdict {}

    record HoldPayout(Set<RedemptionHoldReason> reasons) implements Verdict {}

    record Clear() implements Verdict {}
  }
}
