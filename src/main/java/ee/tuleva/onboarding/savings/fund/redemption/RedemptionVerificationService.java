package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.survey.KycSurveyService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
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
  private final KycSurveyService kycSurveyService;
  private final AmlService amlService;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;

  @Transactional
  public void process(RedemptionRequest request) {
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());

    boolean passed =
        switch (request.getPartyId().type()) {
          case PERSON -> runPersonChecks(request);
          case LEGAL_ENTITY -> runLegalEntityChecks(request);
        };

    if (!passed) {
      log.info(
          "Redemption requires review: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), IN_REVIEW);
    } else {
      log.info(
          "Redemption verification passed: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    }
  }

  private boolean runPersonChecks(RedemptionRequest request) {
    User user =
        userService
            .findByPersonalCode(request.getPartyId().code())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Redemption party user not found: party=" + request.getPartyId()));
    Country country =
        kycSurveyService
            .getCountry(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "KYC survey with country not found: userId=" + user.getId()));

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);
    return checks.stream().allMatch(AmlCheck::isSuccess);
  }

  private boolean runLegalEntityChecks(RedemptionRequest request) {
    var registryCode = request.getPartyId().code();
    if (savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY)) {
      return true;
    }
    var needsScreening =
        savingsFundOnboardingRepository
            .findStatus(registryCode, LEGAL_ENTITY)
            .map(status -> status == PENDING)
            .orElse(true);
    if (!needsScreening) {
      return false;
    }
    try {
      legalEntityScreener.screenLatest(registryCode);
    } catch (RuntimeException e) {
      log.error(
          "Failed to re-screen legal entity for redemption: requestId={}, registryCode={}",
          request.getId(),
          registryCode,
          e);
      return false;
    }
    return savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY);
  }
}
