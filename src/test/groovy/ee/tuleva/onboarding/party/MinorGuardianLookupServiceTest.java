package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterException;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import ee.tuleva.onboarding.populationregister.PopulationRegisterUnavailable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class MinorGuardianLookupServiceTest {

  private static final String REQUESTER = "38001085718";
  private static final String MINOR = "61506150006";
  private static final String OTHER_MINOR = "61508110000";
  private static final String TURNED_ADULT = "60001019906";
  private static final String ADULT = "38812121215";
  private static final String NOT_AN_ESTONIAN_CODE = "AB1234567";
  private static final String MOTHER = "48709090311";
  private static final String FATHER = "39107050268";
  private static final String FORMER_GUARDIAN = "39001109103";
  private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-11-18T03:30:00Z");

  @Autowired private GuardianLookupRepository repository;

  private final ThirdPillarAnalytics thirdPillarAnalytics = mock(ThirdPillarAnalytics.class);
  private final PopulationRegisterClient populationRegisterClient =
      mock(PopulationRegisterClient.class);

  private MinorGuardianLookupService service;

  @BeforeEach
  void setUp() {
    service =
        new MinorGuardianLookupService(
            thirdPillarAnalytics,
            populationRegisterClient,
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void storesTheCustodyHoldersOfAMinorThirdPillarHolderUntilTheirEighteenthBirthday() {
    holders(MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(
                    guardian(MOTHER, PROPERTY_CUSTODY),
                    guardian(MOTHER, PERSONAL_CUSTODY),
                    guardian(FATHER, PERSONAL_CUSTODY)),
                MESSAGE_ID));

    var run = service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(run).isEqualTo(new MinorGuardianLookupRun(1, 0, 0));
    assertThat(repository.findAll())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new GuardianLookup(
                MINOR,
                LocalDate.of(2033, 6, 15),
                NOW,
                MESSAGE_ID,
                Set.of(
                    new LookedUpGuardian(MOTHER, PROPERTY_CUSTODY),
                    new LookedUpGuardian(MOTHER, PERSONAL_CUSTODY),
                    new LookedUpGuardian(FATHER, PERSONAL_CUSTODY))));
  }

  @Test
  void leavesOutACustodyThatIsNoLongerValidOrWhoseHolderHasDied() {
    holders(MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(
                    guardian(MOTHER, PROPERTY_CUSTODY),
                    new Guardian(FORMER_GUARDIAN, PROPERTY_CUSTODY, INVALID, ALIVE, null, null),
                    new Guardian(FATHER, PROPERTY_CUSTODY, VALID, INACTIVE, null, null)),
                MESSAGE_ID));

    service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(repository.findById(MINOR).orElseThrow().getGuardians())
        .containsExactly(new LookedUpGuardian(MOTHER, PROPERTY_CUSTODY));
  }

  @Test
  void recordsAMinorWithoutCustodyHoldersSoTheNextRunDoesNotAskAgain() {
    holders(MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(new PopulationRegisterResult<>(List.of(), MESSAGE_ID));

    service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);
    var secondRun = service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(repository.findById(MINOR).orElseThrow().getGuardians()).isEmpty();
    assertThat(secondRun).isEqualTo(new MinorGuardianLookupRun(0, 0, 0));
    verify(populationRegisterClient).fetchGuardians(REQUESTER, MINOR);
  }

  @Test
  void asksOnlyAboutHoldersWhoAreMinorsToday() {
    holders(TURNED_ADULT, ADULT, NOT_AN_ESTONIAN_CODE);

    var run = service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(run).isEqualTo(new MinorGuardianLookupRun(0, 0, 0));
    verify(populationRegisterClient, never()).fetchGuardians(anyString(), anyString());
  }

  @Test
  void looksUpNoMoreThanTheBatchSizeAndLeavesTheRestForTheNextRun() {
    holders(MINOR, OTHER_MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, OTHER_MINOR))
        .willReturn(new PopulationRegisterResult<>(List.of(), MESSAGE_ID));
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(new PopulationRegisterResult<>(List.of(), MESSAGE_ID));

    var firstRun = service.lookUpMinorThirdPillarHolders(REQUESTER, 1, ZERO);
    var secondRun = service.lookUpMinorThirdPillarHolders(REQUESTER, 1, ZERO);

    assertThat(firstRun).isEqualTo(new MinorGuardianLookupRun(1, 0, 1));
    assertThat(secondRun).isEqualTo(new MinorGuardianLookupRun(1, 0, 0));
    assertThat(repository.findAllChildPersonalCodes())
        .containsExactlyInAnyOrder(MINOR, OTHER_MINOR);
  }

  @Test
  void stopsTheRunWhenTheRegisterIsUnavailable() {
    holders(MINOR, OTHER_MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willThrow(new PopulationRegisterUnavailable("down", new RuntimeException()));

    var run = service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(run).isEqualTo(new MinorGuardianLookupRun(0, 1, 2));
    assertThat(repository.findAll()).isEmpty();
    verify(populationRegisterClient, never()).fetchGuardians(REQUESTER, OTHER_MINOR);
  }

  @Test
  void carriesOnPastAMinorTheRegisterCannotAnswerAndAsksAgainNextRun() {
    holders(MINOR, OTHER_MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, OTHER_MINOR))
        .willThrow(new PopulationRegisterException("no person"));
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(new PopulationRegisterResult<>(List.of(), MESSAGE_ID));

    var run = service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(run).isEqualTo(new MinorGuardianLookupRun(1, 1, 1));
    assertThat(repository.findAllChildPersonalCodes()).containsExactly(MINOR);
  }

  private void holders(String... personalCodes) {
    given(thirdPillarAnalytics.holdersInLatestSnapshot())
        .willReturn(
            List.of(personalCodes).stream()
                .map(code -> new ThirdPillarHolder(code, "First", "Last", "EE"))
                .toList());
  }

  private static Guardian guardian(String personalCode, CustodyRight.Type type) {
    return new Guardian(personalCode, type, VALID, ALIVE, null, null);
  }
}
