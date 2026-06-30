package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildOnboardingService {

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final AmlService amlService;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public ChildOnboardingResult onboardChild(AuthenticatedPerson parent, String childPersonalCode) {
    String parentPersonalCode = parent.getPersonalCode();
    CustodyVerification verification =
        custodyVerificationService.verify(parentPersonalCode, childPersonalCode);

    applicationEventPublisher.publishEvent(
        new TrackableEvent(
            parent,
            TrackableEventType.MINOR_CUSTODY_VERIFICATION,
            Map.of(
                "childPersonalCode", childPersonalCode, "outcome", verification.outcome().name())));

    if (!verification.isVerified()) {
      log.info(
          "Child custody not verified, routing to ops review: parentCode={}, childCode={}, outcome={}",
          parentPersonalCode,
          childPersonalCode,
          verification.outcome());
      amlService.addCustodyRightCheck(
          childPersonalCode, false, Map.of("outcome", verification.outcome().name()));
      return ChildOnboardingResult.underReview();
    }

    PopulationRegisterPerson child = verification.child();
    parentChildLinkRegistrationService.register(
        parentPersonalCode,
        childPersonalCode,
        child.firstName(),
        child.lastName(),
        LEGAL_REPRESENTATIVE);
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(childPersonalCode);
    amlService.addCustodyRightCheck(childPersonalCode, true, verification.evidence());

    return ChildOnboardingResult.verified(child);
  }
}
