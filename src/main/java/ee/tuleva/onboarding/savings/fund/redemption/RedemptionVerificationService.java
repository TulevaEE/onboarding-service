package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.aml.risklevel.RiskLevelService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashSet;
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
  private final AmlService amlService;
  private final RiskLevelService riskLevelService;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final OperationsNotificationService notificationService;

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
      notifyAmlChannel(request);
    } else {
      log.info(
          "Redemption verification passed: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    }
  }

  private void notifyAmlChannel(RedemptionRequest request) {
    try {
      notificationService.sendMessage(
          "AML: redemption held for review: id=%s, amount=%s EUR"
              .formatted(request.getId(), request.getRequestedAmount().toPlainString()),
          AML);
    } catch (RuntimeException e) {
      log.error("Failed to notify AML channel about held redemption: id={}", request.getId(), e);
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
    Set<Country> countries =
        kycCountryService
            .getCountries(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "KYC survey with country not found: userId=" + user.getId()));

    Set<Country> allCountries = new HashSet<>(countries);
    allCountries.addAll(amlService.recordedCitizenships(user));

    boolean screeningClear = amlService.isSanctionAndPepClear(user, allCountries);
    boolean highRisk = riskLevelService.isHighRisk(user.getPersonalCode());
    if (highRisk) {
      log.info(
          "Redemption party is high risk: id={}, party={}", request.getId(), request.getPartyId());
    }
    return screeningClear && !highRisk;
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
