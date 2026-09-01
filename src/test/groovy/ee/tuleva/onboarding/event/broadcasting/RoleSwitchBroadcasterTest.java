package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.ROLE_SWITCH;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RoleSwitchBroadcasterTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private RoleSwitchBroadcaster roleSwitchBroadcaster;

  @Test
  void broadcastsATrackableRoleSwitchForTheOriginalPerson() {
    var originalPerson =
        AuthenticatedPerson.builder()
            .personalCode("38888888888")
            .firstName("First")
            .lastName("Last")
            .build();
    var switchedPerson =
        AuthenticatedPerson.builder()
            .personalCode("38888888888")
            .firstName("First")
            .lastName("Last")
            .role(new Role(RoleType.LEGAL_ENTITY, "10000001", "Sample Company"))
            .build();

    roleSwitchBroadcaster.onRoleSwitched(new RoleSwitchedEvent(originalPerson, switchedPerson));

    then(eventPublisher)
        .should()
        .publishEvent(
            new TrackableEvent(
                originalPerson,
                ROLE_SWITCH,
                Map.of("roleType", "LEGAL_ENTITY", "code", "10000001")));
  }
}
