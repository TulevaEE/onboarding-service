package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_CONNECTED;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.oauth.AppConnected;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AppConnectedBroadcasterTest {

  private static final AuthenticatedPerson PERSON =
      AuthenticatedPerson.builder()
          .personalCode("38888888888")
          .firstName("First")
          .lastName("Last")
          .build();

  @Mock private ApplicationEventPublisher eventPublisher;
  private AppConnectedBroadcaster broadcaster;

  @BeforeEach
  void setUp() {
    broadcaster = new AppConnectedBroadcaster(eventPublisher, new ClientConnection());
  }

  @AfterEach
  void resetRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void broadcastsATrackableConnectionWithTheAddressAndBrowserOfTheApprovingRequest() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.7");
    request.addHeader(USER_AGENT, "Mozilla/5.0");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    broadcaster.onAppConnected(new AppConnected(PERSON, "portfellow", List.of("balances:read")));

    then(eventPublisher)
        .should()
        .publishEvent(
            new TrackableEvent(
                PERSON,
                APP_CONNECTED,
                Map.of(
                    "clientId", "portfellow",
                    "scopes", List.of("balances:read"),
                    "ipAddress", "203.0.113.7",
                    "userAgent", "Mozilla/5.0")));
  }
}
