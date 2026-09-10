package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.PEP_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.SANCTION_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.UNAVAILABLE;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldService.SYSTEM;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.aml.ScreeningOutcome;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionVerificationService {

  static final String SANCTION = "SANCTION";
  static final String PEP = "PEP";
  static final String HIGH_RISK = "HIGH_RISK";
  static final String KYB_NOT_COMPLETED = "KYB_NOT_COMPLETED";
  static final String KYB_SCREENING_FAILED = "KYB_SCREENING_FAILED";

  private final RedemptionStatusService redemptionStatusService;
  private final RedemptionHoldService holdService;
  private final UserService userService;
  private final KycCountryService kycCountryService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final RiskLevels riskLevels;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;

  @Transactional
  public void process(RedemptionRequest request) {
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());

    Verdict verdict =
        switch (request.getPartyId().type()) {
          case PERSON -> verifyPerson(request);
          case LEGAL_ENTITY -> verifyLegalEntity(request);
        };

    switch (verdict.outcome()) {
      case RETRY_LATER ->
          log.warn(
              "Screening unavailable, redemption stays reserved for a retry: id={}, party={}",
              request.getId(),
              request.getPartyId());
      case FREEZE -> holdService.freeze(request.getId(), verdict.reason());
      case HOLD_PAYOUT -> {
        holdService.holdPayout(request.getId(), verdict.reason(), SYSTEM);
        redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      }
      case CLEAR -> {
        log.info(
            "Redemption verification passed: id={}, party={}",
            request.getId(),
            request.getPartyId());
        redemptionStatusService.changeStatus(request.getId(), RESERVED, VERIFIED);
      }
    }
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
    if (screening == UNAVAILABLE) {
      return Verdict.retryLater();
    }
    if (screening == SANCTION_HIT) {
      return Verdict.freeze(SANCTION);
    }
    List<String> reasons = new ArrayList<>();
    if (screening == PEP_HIT) {
      reasons.add(PEP);
    }
    if (riskLevels.isHighRisk(user.getPersonalCode())) {
      reasons.add(HIGH_RISK);
    }
    return reasons.isEmpty() ? Verdict.clear() : Verdict.holdPayout(String.join(",", reasons));
  }

  private Verdict verifyLegalEntity(RedemptionRequest request) {
    var registryCode = request.getPartyId().code();
    if (savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)) {
      return Verdict.clear();
    }
    var needsScreening =
        savingsFundOnboardingRepository
            .findStatus(registryCode, LEGAL_ENTITY)
            .map(status -> status == PENDING)
            .orElse(true);
    if (!needsScreening) {
      return Verdict.holdPayout(KYB_NOT_COMPLETED);
    }
    List<KybCheck> checks;
    try {
      checks = legalEntityScreener.screenLatest(registryCode);
    } catch (RuntimeException e) {
      log.error(
          "Failed to re-screen legal entity for redemption: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return Verdict.holdPayout(KYB_SCREENING_FAILED);
    }
    if (checks.stream().anyMatch(check -> check.type() == COMPANY_SANCTION && !check.success())) {
      return Verdict.freeze(SANCTION);
    }
    return savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)
        ? Verdict.clear()
        : Verdict.holdPayout(KYB_NOT_COMPLETED);
  }

  private record Verdict(Outcome outcome, String reason) {

    enum Outcome {
      RETRY_LATER,
      FREEZE,
      HOLD_PAYOUT,
      CLEAR
    }

    static Verdict retryLater() {
      return new Verdict(Outcome.RETRY_LATER, "");
    }

    static Verdict freeze(String reason) {
      return new Verdict(Outcome.FREEZE, reason);
    }

    static Verdict holdPayout(String reason) {
      return new Verdict(Outcome.HOLD_PAYOUT, reason);
    }

    static Verdict clear() {
      return new Verdict(Outcome.CLEAR, "");
    }
  }
}
