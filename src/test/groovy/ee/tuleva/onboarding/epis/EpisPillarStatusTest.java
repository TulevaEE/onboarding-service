package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.nudge.PillarActivity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EpisPillarStatusTest {

  @Mock ContactDetailsService contactDetailsService;

  @InjectMocks EpisPillarStatus pillarStatus;

  private final Person person = samplePerson();

  @Test
  void aSecondPillarWithAJoiningDateWasJoinedEvenWhenItIsNoLongerActive() {
    given(contactDetailsService.getContactDetails(person))
        .willReturn(
            ContactDetails.builder()
                .isSecondPillarActive(false)
                .isThirdPillarActive(true)
                .secondPillarJoinDate(Instant.parse("2010-01-01T00:00:00Z"))
                .build());

    assertThat(pillarStatus.of(person)).isEqualTo(new PillarActivity(false, true, true));
  }

  @Test
  void aSecondPillarWithoutAJoiningDateWasNeverJoined() {
    given(contactDetailsService.getContactDetails(person))
        .willReturn(
            ContactDetails.builder()
                .isSecondPillarActive(false)
                .isThirdPillarActive(true)
                .secondPillarJoinDate(null)
                .build());

    assertThat(pillarStatus.of(person)).isEqualTo(new PillarActivity(false, true, false));
  }

  @Test
  void anActiveSecondPillarCountsAsJoinedEvenWithoutAJoiningDate() {
    given(contactDetailsService.getContactDetails(person))
        .willReturn(
            ContactDetails.builder()
                .isSecondPillarActive(true)
                .isThirdPillarActive(false)
                .secondPillarJoinDate(null)
                .build());

    assertThat(pillarStatus.of(person)).isEqualTo(new PillarActivity(true, false, true));
  }

  @Test
  void aJoiningDateStillAheadCountsAsJoined() {
    given(contactDetailsService.getContactDetails(person))
        .willReturn(
            ContactDetails.builder()
                .isSecondPillarActive(false)
                .isThirdPillarActive(false)
                .secondPillarJoinDate(Instant.parse("2099-01-01T00:00:00Z"))
                .build());

    assertThat(pillarStatus.of(person)).isEqualTo(new PillarActivity(false, false, true));
  }

  @Test
  void anActiveSecondPillarWasJoined() {
    given(contactDetailsService.getContactDetails(person))
        .willReturn(
            ContactDetails.builder()
                .isSecondPillarActive(true)
                .isThirdPillarActive(false)
                .secondPillarJoinDate(Instant.parse("2010-01-01T00:00:00Z"))
                .build());

    assertThat(pillarStatus.of(person)).isEqualTo(new PillarActivity(true, false, true));
  }
}
