package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;

import ee.tuleva.onboarding.aml.PersonRiskLevel;
import ee.tuleva.onboarding.aml.RiskLevelUnavailableException;
import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Evidence;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Onboarding;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Risk;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.Screening;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.ws.client.WebServiceIOException;

@Component
@Slf4j
@RequiredArgsConstructor
class RedemptionEvidenceCollector {

  private final UserService userService;
  private final KycCountryService kycCountryService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final RiskLevels riskLevels;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;

  Evidence collect(RedemptionRequest request) {
    return switch (request.getPartyId().type()) {
      case PERSON -> personEvidence(request);
      case LEGAL_ENTITY -> legalEntityEvidence(request);
    };
  }

  private Evidence personEvidence(RedemptionRequest request) {
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

    Screening screening = Screening.of(sanctionAndPepScreener.screeningOutcome(user, allCountries));
    return withRiskLevel(
        request, screening, Onboarding.NOT_CHECKED, () -> personRisk(request, user, screening));
  }

  private Evidence legalEntityEvidence(RedemptionRequest request) {
    var registryCode = request.getPartyId().code();
    if (savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)) {
      return withRiskLevel(
          request, Screening.NOT_REPEATED, Onboarding.COMPLETED, () -> companyRisk(registryCode));
    }
    Screening screening = reScreen(request, registryCode);
    Onboarding onboarding =
        screening == Screening.CLEAR
                && savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)
            ? Onboarding.COMPLETED
            : Onboarding.INCOMPLETE;
    return withRiskLevel(request, screening, onboarding, () -> companyRisk(registryCode));
  }

  private Screening reScreen(RedemptionRequest request, String registryCode) {
    List<KybCheck> checks;
    try {
      checks = legalEntityScreener.screenLatest(registryCode);
    } catch (WebServiceIOException | RestClientException e) {
      log.error(
          "Legal entity screening service unavailable: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return Screening.UNAVAILABLE;
    } catch (RuntimeException e) {
      log.error(
          "Failed to re-screen legal entity for redemption: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return Screening.FAILED;
    }
    return checks.stream().anyMatch(check -> check.type() == COMPANY_SANCTION && !check.success())
        ? Screening.SANCTION_HIT
        : Screening.CLEAR;
  }

  private Evidence withRiskLevel(
      RedemptionRequest request,
      Screening screening,
      Onboarding onboarding,
      Supplier<Risk> askedRisk) {
    Risk risk =
        screening.leavesTheRiskLevelUnasked() ? Risk.NOT_ASKED : riskLevel(request, askedRisk);
    return new Evidence(screening, risk, onboarding);
  }

  private Risk riskLevel(RedemptionRequest request, Supplier<Risk> askedRisk) {
    try {
      Risk risk = askedRisk.get();
      if (risk == Risk.HIGH) {
        log.info(
            "Redemption party is high risk: id={}, party={}",
            request.getId(),
            request.getPartyId());
      }
      return risk;
    } catch (RiskLevelUnavailableException _) {
      log.warn(
          "Risk level unavailable: id={}, partyType={}",
          request.getId(),
          request.getPartyId().type());
      return Risk.UNAVAILABLE;
    }
  }

  private Risk personRisk(RedemptionRequest request, User user, Screening screening) {
    PersonRiskLevel level = riskLevels.riskLevel(request.getPartyId().code());
    if (level.isHigh()) {
      return Risk.HIGH;
    }
    if (screening == Screening.PEP_HIT && !pepHitIncludedIn(level, user)) {
      log.info(
          "Redemption party's TKF risk level was not decided after its PEP hit: id={},"
              + " tkfDecidedAt={}",
          request.getId(),
          level.tkfDecidedAt());
      return Risk.DECIDED_BEFORE_PEP_HIT;
    }
    return Risk.NOT_HIGH;
  }

  private boolean pepHitIncludedIn(PersonRiskLevel level, User user) {
    return sanctionAndPepScreener.pepHitAt(user).filter(level::tkfLevelIncludes).isPresent();
  }

  private Risk companyRisk(String registryCode) {
    return riskLevels.isHighRiskCompany(registryCode) ? Risk.HIGH : Risk.NOT_HIGH;
  }
}
