package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_CUSTODY_VERIFICATION;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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

  private Map<String, Object> custodyEvidence(
      CustodyVerification verification,
      String guardianPersonalCode,
      @Nullable String guardianCitizenship) {
    var evidence = new LinkedHashMap<String, Object>(verification.evidenceWithCitizenships());
    if (verification.isVerified()) {
      evidence.put("guardianPersonalCode", guardianPersonalCode);
      if (guardianCitizenship != null) {
        evidence.put("guardianCitizenship", guardianCitizenship);
      }
    }
    return unmodifiableMap(evidence);
  }

  private void screenForSanctionsAndPep(PopulationRegisterPerson child) {
    amlService.addSanctionAndPepCheckIfMissing(
        new PersonImpl(child.personalCode(), child.firstName(), child.lastName()),
        Countries.of(child.citizenships()));
  }

  private void screenGuardian(AuthenticatedPerson parent, @Nullable String citizenship) {
    amlService.addSanctionAndPepCheckIfMissing(
        new PersonImpl(parent.getPersonalCode(), parent.getFirstName(), parent.getLastName()),
        Countries.of(citizenship));
  }

  private boolean hasBeenOnboarded(String childPersonalCode) {
    return savingsFundOnboardingService.isOnboardingCompleted(
        new PartyId(PERSON, childPersonalCode));
  }

  @Transactional
  public ChildOnboardingResult onboardChild(AuthenticatedPerson parent, String childPersonalCode) {
    String parentPersonalCode = parent.getPersonalCode();
    CustodyVerification verification =
        custodyVerificationService.verify(parentPersonalCode, childPersonalCode, CUSTODY_MAX_AGE);

    applicationEventPublisher.publishEvent(
        new TrackableEvent(
            parent, MINOR_CUSTODY_VERIFICATION, new HashMap<>(verification.evidence())));
    String guardianCitizenship =
        verification.isVerified()
            ? custodyVerificationService.fetchGuardianCitizenship(
                parentPersonalCode, CUSTODY_MAX_AGE)
            : null;
    amlService.addCustodyRightCheck(
        childPersonalCode,
        verification.isVerified(),
        custodyEvidence(verification, parentPersonalCode, guardianCitizenship));

    if (!verification.isVerified()) {
      log.info(
          "Child custody not verified, routing to ops review: parentCode={}, childCode={}, outcome={}",
          parentPersonalCode,
          childPersonalCode,
          verification.outcome());
      return ChildOnboardingResult.underReview();
    }

    PopulationRegisterPerson child =
        requireNonNull(verification.child(), "Verified custody without child data");
    parentChildLinkRegistrationService.register(
        parentPersonalCode, childPersonalCode, child.firstName(), child.lastName());
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(childPersonalCode);
    screenForSanctionsAndPep(child);
    screenGuardian(parent, guardianCitizenship);

    applicationEventPublisher.publishEvent(
        new ChildOnboardedEvent(
            parentPersonalCode, childPersonalCode, child.firstName(), child.lastName()));

    return ChildOnboardingResult.verified(child);
  }
}
