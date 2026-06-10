package ee.tuleva.onboarding.party;

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
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ParentChildLink register(
      String parentPersonalCode,
      String childPersonalCode,
      String childFirstName,
      String childLastName,
      RepresentationType relationshipType) {

    LocalDate dateOfBirth = PersonalCode.getDateOfBirth(childPersonalCode);
    LocalDate eighteenthBirthday = dateOfBirth.plusYears(18);
    LocalDate today = LocalDate.now(clock);
    if (dateOfBirth.isAfter(today) || !eighteenthBirthday.isAfter(today)) {
      throw new ChildIsNotAMinorException(childPersonalCode);
    }

    upsertChild(childPersonalCode, childFirstName, childLastName);

    ParentChildLink link =
        parentChildLinkRepository
            .findByParentPersonalCodeAndChildPersonalCodeAndRelationshipType(
                parentPersonalCode, childPersonalCode, relationshipType)
            .orElseGet(
                () -> {
                  log.info(
                      "Creating parent-child link: parentCode={}, childCode={}, validUntil={}",
                      parentPersonalCode,
                      childPersonalCode,
                      eighteenthBirthday);
                  return parentChildLinkRepository.save(
                      ParentChildLink.builder()
                          .parentPersonalCode(parentPersonalCode)
                          .childPersonalCode(childPersonalCode)
                          .relationshipType(relationshipType)
                          .validUntil(eighteenthBirthday)
                          .build());
                });

    eventPublisher.publishEvent(new ParentChildLinkRegisteredEvent(childPersonalCode));

    return link;
  }

  private void upsertChild(String childPersonalCode, String firstName, String lastName) {
    userService
        .findByPersonalCode(childPersonalCode)
        .ifPresentOrElse(
            existing -> {
              existing.setFirstName(capitalizeFully(firstName, ' ', '-'));
              existing.setLastName(capitalizeFully(lastName, ' ', '-'));
              userService.save(existing);
            },
            () ->
                userService.createNewUser(
                    User.builder()
                        .personalCode(childPersonalCode)
                        .firstName(capitalizeFully(firstName, ' ', '-'))
                        .lastName(capitalizeFully(lastName, ' ', '-'))
                        .active(true)
                        .build()));
  }
}
