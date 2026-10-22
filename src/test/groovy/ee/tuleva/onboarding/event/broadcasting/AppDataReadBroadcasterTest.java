package ee.tuleva.onboarding.event.broadcasting;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_DATA_READ;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.oauth.AppDataRead;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppDataReadBroadcasterTest {

  private static final AuthenticatedPerson PERSON =
      AuthenticatedPerson.builder()
          .personalCode("38812121215")
          .firstName("Jordan")
          .lastName("Valdma")
          .build();
  private static final UUID GRANT_ID = UUID.fromString("5b0c1d2e-3f4a-4b5c-8d6e-7f8a9b0c1d2e");

  private final List<Object> published = new ArrayList<>();
  private final AppDataReadBroadcaster broadcaster = new AppDataReadBroadcaster(published::add);

  @Test
  void recordsTheReadForThePersonWithTheAppItsGrantTheEndpointAndTheScopes() {
    broadcaster.onAppDataRead(
        new AppDataRead(
            PERSON,
            "portfellow",
            GRANT_ID,
            "GET /v1/savings-account-statement",
            List.of("balances:read")));

    assertThat(published)
        .containsExactly(
            new TrackableEvent(
                PERSON,
                APP_DATA_READ,
                Map.of(
                    "clientId",
                    "portfellow",
                    "grantId",
                    GRANT_ID.toString(),
                    "endpoint",
                    "GET /v1/savings-account-statement",
                    "scopes",
                    List.of("balances:read"))));
  }
}
