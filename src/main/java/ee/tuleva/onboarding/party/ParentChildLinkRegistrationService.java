package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import ee.tuleva.onboarding.party.GuardianLinkRegistration.Created;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.EndDateChanged;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.Unchanged;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.user.NameChangeBasis;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentChildLinkRegistrationService {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final UserService userService;
  private final Clock clock;

  @Transactional
  public ParentChildLink register(
      String parentPersonalCode,
      String childPersonalCode,
      String childFirstName,
      String childLastName,
      NameChangeBasis nameBasis) {

    requireMinor(childPersonalCode);
    upsertPerson(childPersonalCode, childFirstName, childLastName, nameBasis);
    return findOrCreateLink(
        parentPersonalCode,
        childPersonalCode,
        LEGAL_REPRESENTATIVE,
        eighteenthBirthday(childPersonalCode));
  }

  // REQUIRES_NEW: the only caller is an AFTER_COMMIT listener, where a REQUIRED transaction would
  // join the already-committed transaction and its writes would never be committed.
  @Transactional(propagation = REQUIRES_NEW)
  public ParentChildLink registerPending(
      Guardian coParent, String childPersonalCode, String childFirstName, String childLastName) {

    requireMinor(childPersonalCode);
    upsertPerson(
        childPersonalCode, childFirstName, childLastName, NameChangeBasis.populationRegister());
    return parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            coParent.personalCode(), childPersonalCode, LEGAL_REPRESENTATIVE)
        .map(existing -> withRecordedName(existing, coParent))
        .orElseGet(() -> createPendingLink(coParent, childPersonalCode));
  }

  private ParentChildLink withRecordedName(ParentChildLink existing, Guardian coParent) {
    String firstName = coParent.firstName();
    String lastName = coParent.lastName();
    if (!existing.isPending()
        || existing.hasRecordedParentName()
        || firstName == null
        || lastName == null) {
      return existing;
    }
    existing.recordParentName(firstName, lastName);
    return parentChildLinkRepository.save(existing);
  }

  private ParentChildLink createPendingLink(Guardian coParent, String childPersonalCode) {
    log.info(
        "Capturing pending parent-child link: coParentCode={}, childCode={}",
        coParent.personalCode(),
        childPersonalCode);
    return parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(coParent.personalCode())
            .childPersonalCode(childPersonalCode)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(eighteenthBirthday(childPersonalCode))
            .status(PENDING_KYC)
            .parentFirstName(coParent.firstName())
            .parentLastName(coParent.lastName())
            .build());
  }

  @Transactional
  public GuardianLinkRegistration registerGuardian(
      String guardianPersonalCode,
      String wardPersonalCode,
      String wardFirstName,
      String wardLastName,
      LocalDate validUntil,
      NameChangeBasis nameBasis) {

    upsertPerson(wardPersonalCode, wardFirstName, wardLastName, nameBasis);
    return parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            guardianPersonalCode, wardPersonalCode, GUARDIAN)
        .map(existing -> renew(existing, validUntil))
        .orElseGet(
            () ->
                new Created(
                    createLink(guardianPersonalCode, wardPersonalCode, GUARDIAN, validUntil)));
  }

  private GuardianLinkRegistration renew(ParentChildLink existing, LocalDate validUntil) {
    LocalDate previousValidUntil = existing.getValidUntil();
    if (previousValidUntil.equals(validUntil)) {
      return new Unchanged(existing);
    }
    log.info(
        "Changing guardian link end date: linkId={}, previousValidUntil={}, validUntil={},"
            + " suspended={}",
        existing.getId(),
        previousValidUntil,
        validUntil,
        existing.isSuspended());
    existing.changeValidUntil(validUntil);
    return new EndDateChanged(parentChildLinkRepository.save(existing), previousValidUntil);
  }

  private ParentChildLink findOrCreateLink(
      String parentPersonalCode,
      String childPersonalCode,
      RepresentationType relationshipType,
      LocalDate validUntil) {
    return parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            parentPersonalCode, childPersonalCode, relationshipType)
        .map(existing -> existing.isPending() ? activate(existing) : existing)
        .orElseGet(
            () -> createLink(parentPersonalCode, childPersonalCode, relationshipType, validUntil));
  }

  private ParentChildLink createLink(
      String parentPersonalCode,
      String childPersonalCode,
      RepresentationType relationshipType,
      LocalDate validUntil) {
    log.info(
        "Creating parent-child link: parentCode={}, childCode={}, relationshipType={}, validUntil={}",
        parentPersonalCode,
        childPersonalCode,
        relationshipType,
        validUntil);
    return parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentPersonalCode)
            .childPersonalCode(childPersonalCode)
            .relationshipType(relationshipType)
            .validUntil(validUntil)
            .build());
  }

  private ParentChildLink activate(ParentChildLink pending) {
    log.info(
        "Activating pending parent-child link: parentCode={}, childCode={}",
        pending.getParentPersonalCode(),
        pending.getChildPersonalCode());
    pending.activate();
    return parentChildLinkRepository.save(pending);
  }

  private void requireMinor(String childPersonalCode) {
    if (!PersonalCode.isMinor(childPersonalCode, LocalDate.now(clock))) {
      throw new ChildIsNotAMinorException(childPersonalCode);
    }
  }

  private LocalDate eighteenthBirthday(String childPersonalCode) {
    return PersonalCode.getDateOfBirth(childPersonalCode).plusYears(18);
  }

  private void upsertPerson(
      String personalCode, String firstName, String lastName, NameChangeBasis nameBasis) {
    if (userService.findByPersonalCode(personalCode).isPresent()) {
      userService.changeName(personalCode, formatted(firstName), formatted(lastName), nameBasis);
      return;
    }
    userService.createNewUser(
        User.builder()
            .personalCode(personalCode)
            .firstName(formatted(firstName))
            .lastName(formatted(lastName))
            .active(true)
            .build(),
        nameBasis);
  }
}
