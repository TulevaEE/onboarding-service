package ee.tuleva.onboarding.populationregister;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MockPopulationRegisterClientTest {

  private final MockPopulationRegisterClient client =
      new MockPopulationRegisterClient(JsonMapper.builder().build());

  @Test
  void servesAPersonFromTheBundledFixture() {
    var result = client.fetchPerson("38888888888", "48888888888", Duration.ZERO);

    assertThat(result.data().personalCode()).isNotBlank();
    assertThat(result.messageId()).isNotNull();
  }

  @Test
  void freshPersonLookupServesTheSameFixture() {
    assertThat(client.fetchPersonFresh("38888888888", "48888888888").data().personalCode())
        .isEqualTo(
            client.fetchPerson("38888888888", "48888888888", Duration.ZERO).data().personalCode());
  }

  @Test
  void servesCustodyRightsFromTheBundledFixture() {
    assertThat(client.fetchCustodyRights("38888888888", Duration.ZERO).data()).isNotEmpty();
    assertThat(client.fetchCustodyRightsFresh("38888888888", "38888888888").data()).isNotEmpty();
  }

  @Test
  void servesGuardiansFromTheBundledFixture() {
    assertThat(client.fetchGuardians("38888888888", "48888888888").data()).isNotEmpty();
  }
}
