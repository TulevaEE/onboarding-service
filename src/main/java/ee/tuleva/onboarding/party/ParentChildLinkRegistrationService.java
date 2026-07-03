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

    LocalDate dateOfBirth = PersonalCode.getDateOfBirth(childPersonalCode);
    LocalDate eighteenthBirthday = dateOfBirth.plusYears(18);
    LocalDate today = LocalDate.now(clock);
    if (dateOfBirth.isAfter(today) || !eighteenthBirthday.isAfter(today)) {
      throw new ChildIsNotAMinorException(childPersonalCode);
    }

    upsertPerson(childPersonalCode, childFirstName, childLastName);
    return findOrCreateLink(
        parentPersonalCode, childPersonalCode, LEGAL_REPRESENTATIVE, eighteenthBirthday);
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
