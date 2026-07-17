package ee.tuleva.onboarding.account;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.epis.EpisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnRoleSwitchEpisCacheClearerTest {

  @Mock private EpisService episService;

  @InjectMocks private OnRoleSwitchEpisCacheClearer cacheClearer;

  @Test
  void clearsTheCacheOfTheRepresentedPersonOnRoleSwitch() {
    var representingChild =
        sampleAuthenticatedPersonAndMember()
            .role(new Role(PERSON, "61506150006", "Mari Maasikas"))
            .build();

    cacheClearer.onRoleSwitched(new RoleSwitchedEvent(representingChild));

    verify(episService).clearCache(representingChild);
  }
}
