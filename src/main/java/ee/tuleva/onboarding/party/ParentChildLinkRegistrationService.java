package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentChildLinkRegistrationService {

  private final ParentChildLinkRepository parentChildLinkRepository;
  private final UserService userService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;

  @Transactional
  public ParentChildLink register(
      String parentPersonalCode,
      String childPersonalCode,
      String childFirstName,
      String childLastName) {

    requireMinor(childPersonalCode);
    upsertPerson(childPersonalCode, childFirstName, childLastName);
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
      String coParentPersonalCode,
      String childPersonalCode,
      String childFirstName,
      String childLastName) {

    requireMinor(childPersonalCode);
    upsertPerson(childPersonalCode, childFirstName, childLastName);
    return parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            coParentPersonalCode, childPersonalCode, LEGAL_REPRESENTATIVE)
        .orElseGet(() -> createPendingLink(coParentPersonalCode, childPersonalCode));
  }

  private ParentChildLink createPendingLink(String coParentPersonalCode, String childPersonalCode) {
    log.info(
        "Capturing pending parent-child link: coParentCode={}, childCode={}",
        coParentPersonalCode,
        childPersonalCode);
    return parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(coParentPersonalCode)
            .childPersonalCode(childPersonalCode)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(eighteenthBirthday(childPersonalCode))
            .status(PENDING_KYC)
            .build());
  }

  @Transactional
  public ParentChildLink registerGuardian(
      String guardianPersonalCode,
      String wardPersonalCode,
      String wardFirstName,
      String wardLastName,
      LocalDate validUntil) {

    upsertPerson(wardPersonalCode, wardFirstName, wardLastName);
    return findOrCreateLink(guardianPersonalCode, wardPersonalCode, GUARDIAN, validUntil);
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
    ParentChildLink saved =
        parentChildLinkRepository.save(
            ParentChildLink.builder()
                .parentPersonalCode(parentPersonalCode)
                .childPersonalCode(childPersonalCode)
                .relationshipType(relationshipType)
                .validUntil(validUntil)
                .build());
    publishCreated(saved);
    return saved;
  }

  private ParentChildLink activate(ParentChildLink pending) {
    log.info(
        "Activating pending parent-child link: parentCode={}, childCode={}",
        pending.getParentPersonalCode(),
        pending.getChildPersonalCode());
    pending.activate();
    ParentChildLink saved = parentChildLinkRepository.save(pending);
    publishCreated(saved);
    return saved;
  }

  private void publishCreated(ParentChildLink link) {
    applicationEventPublisher.publishEvent(
        new ParentChildLinkCreatedEvent(
            link.getParentPersonalCode(), link.getChildPersonalCode(), link.getRelationshipType()));
  }

  private void requireMinor(String childPersonalCode) {
    if (!PersonalCode.isMinor(childPersonalCode, LocalDate.now(clock))) {
      throw new ChildIsNotAMinorException(childPersonalCode);
    }
  }

  private LocalDate eighteenthBirthday(String childPersonalCode) {
    return PersonalCode.getDateOfBirth(childPersonalCode).plusYears(18);
  }

  private void upsertPerson(String personalCode, String firstName, String lastName) {
    userService
        .findByPersonalCode(personalCode)
        .ifPresentOrElse(
            existing -> {
              existing.setFirstName(capitalizeFully(firstName, ' ', '-'));
              existing.setLastName(capitalizeFully(lastName, ' ', '-'));
              userService.save(existing);
            },
            () ->
                userService.createNewUser(
                    User.builder()
                        .personalCode(personalCode)
                        .firstName(capitalizeFully(firstName, ' ', '-'))
                        .lastName(capitalizeFully(lastName, ' ', '-'))
                        .active(true)
                        .build()));
  }
}
