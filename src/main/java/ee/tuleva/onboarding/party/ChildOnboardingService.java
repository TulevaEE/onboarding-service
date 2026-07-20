package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

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

  public List<EligibleChild> findEligibleChildren(AuthenticatedPerson parent) {
    LocalDate today = LocalDate.now(clock);
    return custodyVerificationService
        .findChildrenWithAssetManagementCustody(parent.getPersonalCode(), CUSTODY_MAX_AGE)
        .stream()
        .filter(right -> PersonalCode.isMinor(right.childPersonalCode(), today))
        .map(
            right ->
                new EligibleChild(
                    right.childPersonalCode(),
                    right.firstName(),
                    right.lastName(),
                    hasBeenOnboarded(right.childPersonalCode())))
        .toList();
  }

  private boolean hasBeenOnboarded(String childPersonalCode) {
    return savingsFundOnboardingService.getOnboardingStatus(new PartyId(PERSON, childPersonalCode))
        != null;
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

    capturePendingCoParents(parentPersonalCode, child);

    return ChildOnboardingResult.verified(child);
  }

  // Give each OTHER guardian a PENDING_KYC link so the child shows in their account selector; they
  // gain access only after their own onboarding/KYC activates it. Best-effort inside the parent's
  // transaction: capturing co-parents must never break the opening parent's own onboarding.
  private void capturePendingCoParents(String parentPersonalCode, PopulationRegisterPerson child) {
    try {
      custodyVerificationService
          .findGuardiansWithAssetManagement(child.personalCode(), parentPersonalCode)
          .forEach(
              coParentPersonalCode ->
                  parentChildLinkRegistrationService.registerPending(
                      coParentPersonalCode,
                      child.personalCode(),
                      child.firstName(),
                      child.lastName()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to capture pending co-parents, continuing onboarding: parentCode={}, childCode={}",
          parentPersonalCode,
          child.personalCode(),
          e);
    }
  }
}
