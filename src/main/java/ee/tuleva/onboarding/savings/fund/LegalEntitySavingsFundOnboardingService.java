package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.TkfOnboardingDecision.statusAfter;

import ee.tuleva.onboarding.aml.OnboardingReview.Outcome;
import ee.tuleva.onboarding.company.BoardMembershipService;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.TkfOnboardingDecision.Review;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntitySavingsFundOnboardingService {

  private final BoardMembershipService boardMembershipService;
  private final SavingsFundOnboardingRepository onboardingRepository;
  private final CompanyRepository companyRepository;
  private final ApplicationEventPublisher eventPublisher;

  public Optional<SavingsFundOnboardingStatus> getOnboardingStatus(
      String personalCode, String registryCode) {
    if (!boardMembershipService.isBoardMember(personalCode, registryCode)) {
      return Optional.empty();
    }
    return onboardingRepository.findStatus(registryCode, LEGAL_ENTITY);
  }

  public boolean isOnboardingCompleted(String personalCode, String registryCode) {
    if (!boardMembershipService.isBoardMember(personalCode, registryCode)) {
      return false;
    }
    return onboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY);
  }

  @Transactional
  public void applyReview(String registryCode, Outcome outcome) {
    var oldStatus = onboardingRepository.findStatus(registryCode, LEGAL_ENTITY).orElse(null);
    var newStatus = statusAfter(new Review(oldStatus, outcome));
    if (oldStatus == null
        || newStatus == null
        || newStatus == oldStatus
        || !onboardingRepository.changeStatus(registryCode, LEGAL_ENTITY, oldStatus, newStatus)) {
      return;
    }
    log.info(
        "Legal entity onboarding reviewed: registryCode={}, oldStatus={}, newStatus={}",
        registryCode,
        oldStatus,
        newStatus);
    if (newStatus == COMPLETED) {
      eventPublisher.publishEvent(
          new LegalEntityOnboardedEvent(
              this, new RegistryCode(registryCode), companyName(registryCode)));
    }
  }

  private String companyName(String registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode)
        .map(Company::getName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Reviewed company not found: registryCode=" + registryCode));
  }
}
