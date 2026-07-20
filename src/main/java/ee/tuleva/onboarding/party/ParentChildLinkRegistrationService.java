package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

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

  // Captures a PENDING_KYC link for the OTHER guardian discovered from the population register when
  // the account is opened. It publishes NO ParentChildLinkCreatedEvent (that event's listener emails
  // the parents; a pending capture must be silent) and grants no access. Idempotent find-first: an
  // existing link (of either status) is left untouched, so an ACTIVE link is never downgraded to
  // pending and re-capture never duplicates. ChildOnboardingService wraps the call so that, in the
  // narrow race where two guardians open the same child concurrently, a unique-constraint failure is
  // logged rather than breaking the opening parent's onboarding.
  @Transactional
  public void registerPending(
      String coParentPersonalCode,
      String childPersonalCode,
      String childFirstName,
      String childLastName) {

    requireMinor(childPersonalCode);
    upsertPerson(childPersonalCode, childFirstName, childLastName);
    if (parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            coParentPersonalCode, childPersonalCode, LEGAL_REPRESENTATIVE)
        .isPresent()) {
      return;
    }
    log.info(
        "Capturing pending parent-child link: coParentCode={}, childCode={}",
        coParentPersonalCode,
        childPersonalCode);
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(coParentPersonalCode)
            .childPersonalCode(childPersonalCode)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(eighteenthBirthday(childPersonalCode))
            .status(ParentChildLinkStatus.PENDING_KYC)
            .build());
  }

  // Activates the co-parent's PENDING_KYC link once they have completed their own onboarding/KYC.
  // This is the real "co-parent added" moment, so it publishes ParentChildLinkCreatedEvent (unlike
  // register/findOrCreateLink, which never update an existing row — activation must be explicit).
  // A no-op when there is no pending link (already active, or never captured).
  @Transactional
  public void activate(String parentPersonalCode, String childPersonalCode) {
    parentChildLinkRepository
        .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
            parentPersonalCode, childPersonalCode, LEGAL_REPRESENTATIVE)
        .filter(ParentChildLink::isPending)
        .ifPresent(
            link -> {
              log.info(
                  "Activating pending parent-child link: parentCode={}, childCode={}",
                  parentPersonalCode,
                  childPersonalCode);
              link.activate();
              parentChildLinkRepository.save(link);
              applicationEventPublisher.publishEvent(
                  new ParentChildLinkCreatedEvent(
                      parentPersonalCode, childPersonalCode, link.getRelationshipType()));
            });
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
        .orElseGet(
            () -> {
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
              applicationEventPublisher.publishEvent(
                  new ParentChildLinkCreatedEvent(
                      parentPersonalCode, childPersonalCode, relationshipType));
              return saved;
            });
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
