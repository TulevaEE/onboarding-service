package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.auth.AuthenticationTokensFixture.sampleAuthenticationTokens;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_SELF_LOGIN;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.event.TrackableEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MinorSelfLoginBroadcasterTest {

  private static final String MINOR = "61506150006";
  private static final String ADULT = "38812121215";

  @Mock private ApplicationEventPublisher eventPublisher;

  private MinorSelfLoginBroadcaster minorSelfLoginBroadcaster;

  @BeforeEach
  void setUp() {
    minorSelfLoginBroadcaster =
        new MinorSelfLoginBroadcaster(
            eventPublisher, Clock.fixed(Instant.parse("2026-05-22T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void broadcastsATrackableMinorSelfLoginWhenAMinorLogsInAsThemselves() {
    var minor = personActingAs(MINOR, MINOR);

    minorSelfLoginBroadcaster.onAfterTokenGranted(
        new AfterTokenGrantedEvent(this, minor, SMART_ID, sampleAuthenticationTokens()));

    then(eventPublisher)
        .should()
        .publishEvent(new TrackableEvent(minor, MINOR_SELF_LOGIN, Map.of("method", SMART_ID)));
  }

  @Test
  void broadcastsNothingWhenAnAdultLogsIn() {
    var adult = personActingAs(ADULT, ADULT);

    minorSelfLoginBroadcaster.onAfterTokenGranted(
        new AfterTokenGrantedEvent(this, adult, SMART_ID, sampleAuthenticationTokens()));

    then(eventPublisher).shouldHaveNoInteractions();
  }

  private static AuthenticatedPerson personActingAs(String personalCode, String roleCode) {
    return AuthenticatedPerson.builder()
        .personalCode(personalCode)
        .firstName("Mari")
        .lastName("Maasikas")
        .role(new Role(PERSON, roleCode, "Mari Maasikas"))
        .build();
  }
}
