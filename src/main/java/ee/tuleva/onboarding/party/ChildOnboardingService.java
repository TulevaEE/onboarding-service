package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildOnboardingService {

  static final Duration CUSTODY_MAX_AGE = Duration.ofHours(24);

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final AmlService amlService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;

  public List<String> findEligibleChildren(AuthenticatedPerson parent) {
    LocalDate today = LocalDate.now(clock);
    return custodyVerificationService
        .findChildrenWithAssetManagementCustody(parent.getPersonalCode(), CUSTODY_MAX_AGE)
        .stream()
        .filter(childPersonalCode -> PersonalCode.isMinor(childPersonalCode, today))
        .toList();
  }

  @Transactional
  public ChildOnboardingResult onboardChild(AuthenticatedPerson parent, String childPersonalCode) {
    String parentPersonalCode = parent.getPersonalCode();
    CustodyVerification verification =
        custodyVerificationService.verify(parentPersonalCode, childPersonalCode, CUSTODY_MAX_AGE);

    applicationEventPublisher.publishEvent(
        new TrackableEvent(
            parent, TrackableEventType.MINOR_CUSTODY_VERIFICATION, verification.evidence()));
    amlService.addCustodyRightCheck(
        childPersonalCode, verification.isVerified(), verification.evidence());

    if (!verification.isVerified()) {
      log.info(
          "Child custody not verified, routing to ops review: parentCode={}, childCode={}, outcome={}",
          parentPersonalCode,
          childPersonalCode,
          verification.outcome());
      return ChildOnboardingResult.underReview();
    }

    PopulationRegisterPerson child = verification.child();
    parentChildLinkRegistrationService.register(
        parentPersonalCode, childPersonalCode, child.firstName(), child.lastName());
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(childPersonalCode);

    return ChildOnboardingResult.verified(child);
  }
}
