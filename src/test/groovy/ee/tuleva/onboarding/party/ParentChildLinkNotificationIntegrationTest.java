package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ParentChildLinkNotificationIntegrationTest {

  private static final String NEW_PARENT = "37707070775";
  private static final String EXISTING_PARENT = "38808080887";
  private static final String CHILD = "61105110550";

  @Autowired private ParentChildLinkRegistrationService registrationService;
  @Autowired private ParentChildLinkRepository parentChildLinkRepository;
  @Autowired private UserRepository userRepository;

  @MockitoBean private EmailService emailService;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    parentChildLinkRepository.deleteAll(
        parentChildLinkRepository
            .findByChildPersonalCodeAndStatusAndSuspendedAtIsNullAndValidUntilAfter(
                CHILD, ParentChildLinkStatus.ACTIVE, LocalDate.now().minusYears(100)));
    Stream.of(NEW_PARENT, EXISTING_PARENT, CHILD)
        .map(userRepository::findByPersonalCode)
        .flatMap(Optional::stream)
        .forEach(userRepository::delete);
  }

  @Test
  void notifiesNewParentAndExistingParentsWhenNewParentLinks() {
    saveUser(NEW_PARENT, "new.parent@example.com");
    User existingParent = saveUser(EXISTING_PARENT, "existing.parent@example.com");
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(EXISTING_PARENT)
            .childPersonalCode(CHILD)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .validUntil(LocalDate.now().plusYears(5))
            .build());

    registrationService.register(NEW_PARENT, CHILD, "New", "Parent");

    verify(emailService)
        .send(
            argThat(user -> NEW_PARENT.equals(user.getPersonalCode())),
            any(),
            eq("parent_child_link_confirmation_et"));
    verify(emailService)
        .send(
            argThat(user -> existingParent.getPersonalCode().equals(user.getPersonalCode())),
            any(),
            eq("parent_child_link_added_et"));
  }

  private User saveUser(String personalCode, String email) {
    return userRepository.save(
        User.builder()
            .personalCode(personalCode)
            .firstName("First")
            .lastName("Last")
            .email(email)
            .active(true)
            .build());
  }
}
