package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_CUSTODY_VERIFICATION;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import ee.tuleva.onboarding.user.NameChangeBasis;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  private static final String CUSTODY_VOUCHED_FOR_BY_GUARDIAN_LINK = "GUARDIAN_LINK";

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final ChildSavingsOnboarding childSavingsOnboarding;
  private final AmlService amlService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ParentChildLinkService parentChildLinkService;
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
    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(
        new PersonImpl(child.personalCode(), child.firstName(), child.lastName()),
        Countries.of(child.citizenships()));
  }

  private void screenGuardian(AuthenticatedPerson parent, @Nullable String citizenship) {
    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(
        new PersonImpl(parent.getPersonalCode(), parent.getFirstName(), parent.getLastName()),
        Countries.of(citizenship));
  }

  private boolean hasBeenOnboarded(String childPersonalCode) {
    return childSavingsOnboarding.isCompleted(childPersonalCode);
  }

  @Transactional
  public ChildOnboardingResult onboardChild(AuthenticatedPerson parent, String childPersonalCode) {
    return findGuardianshipOfAnAdultWard(parent.getPersonalCode(), childPersonalCode)
        .map(guardianship -> onboardWard(parent, guardianship))
        .orElseGet(() -> onboardChildWithRegisterCustody(parent, childPersonalCode));
  }

  private Optional<ParentChildLink> findGuardianshipOfAnAdultWard(
      String guardianPersonalCode, String wardPersonalCode) {
    if (PersonalCode.isMinor(wardPersonalCode, LocalDate.now(clock))) {
      return Optional.empty();
    }
    return parentChildLinkService.findActiveGuardianship(guardianPersonalCode, wardPersonalCode);
  }

  private ChildOnboardingResult onboardWard(
      AuthenticatedPerson guardian, ParentChildLink guardianship) {
    String guardianPersonalCode = guardian.getPersonalCode();
    String wardPersonalCode = guardianship.getChildPersonalCode();
    PopulationRegisterResult<PopulationRegisterPerson> wardResult =
        custodyVerificationService.fetchWard(
            guardianPersonalCode, wardPersonalCode, CUSTODY_MAX_AGE);
    PopulationRegisterPerson ward = wardResult.data();

    if (!ward.isAlive()) {
      amlService.addCustodyRightCheck(
          wardPersonalCode,
          false,
          guardianLinkEvidence(CHILD_NOT_ALIVE.name(), guardianship, wardResult.messageId()));
      log.info(
          "Ward not alive in the population register, routing to ops review:"
              + " guardianCode={}, wardCode={}, linkId={}",
          guardianPersonalCode,
          wardPersonalCode,
          guardianship.getId());
      return ChildOnboardingResult.underReview();
    }

    String guardianCitizenship =
        custodyVerificationService.fetchGuardianCitizenship(guardianPersonalCode, CUSTODY_MAX_AGE);
    amlService.addCustodyRightCheck(
        wardPersonalCode,
        true,
        verifiedGuardianLinkEvidence(guardianship, wardResult, guardianCitizenship));
    childSavingsOnboarding.seedIfAbsent(wardPersonalCode);
    screenForSanctionsAndPep(ward);
    screenGuardian(guardian, guardianCitizenship);

    log.info(
        "Ward custody vouched for by guardian link: guardianCode={}, wardCode={}, linkId={}",
        guardianPersonalCode,
        wardPersonalCode,
        guardianship.getId());
    return ChildOnboardingResult.verified(ward);
  }

  private Map<String, Object> verifiedGuardianLinkEvidence(
      ParentChildLink guardianship,
      PopulationRegisterResult<PopulationRegisterPerson> wardResult,
      @Nullable String guardianCitizenship) {
    var evidence =
        new LinkedHashMap<String, Object>(
            guardianLinkEvidence(
                CUSTODY_VOUCHED_FOR_BY_GUARDIAN_LINK, guardianship, wardResult.messageId()));
    evidence.put("guardianPersonalCode", guardianship.getParentPersonalCode());
    if (guardianCitizenship != null) {
      evidence.put("guardianCitizenship", guardianCitizenship);
    }
    return CustodyVerification.withCitizenships(evidence, wardResult.data());
  }

  private Map<String, Object> guardianLinkEvidence(
      String outcome, ParentChildLink guardianship, UUID wardResponseMessageId) {
    var evidence = new LinkedHashMap<String, Object>();
    evidence.put("outcome", outcome);
    evidence.put("childPersonalCode", guardianship.getChildPersonalCode());
    evidence.put("guardianLinkId", guardianship.getId().toString());
    evidence.put("guardianLinkValidUntil", guardianship.getValidUntil().toString());
    evidence.put("childResponseMessageId", wardResponseMessageId.toString());
    return unmodifiableMap(evidence);
  }

  private ChildOnboardingResult onboardChildWithRegisterCustody(
      AuthenticatedPerson parent, String childPersonalCode) {
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
        parentPersonalCode,
        childPersonalCode,
        child.firstName(),
        child.lastName(),
        NameChangeBasis.populationRegister());
    childSavingsOnboarding.seedIfAbsent(childPersonalCode);
    screenForSanctionsAndPep(child);
    screenGuardian(parent, guardianCitizenship);

    applicationEventPublisher.publishEvent(
        new ChildOnboardedEvent(
            parentPersonalCode, childPersonalCode, child.firstName(), child.lastName()));

    return ChildOnboardingResult.verified(child);
  }
}
