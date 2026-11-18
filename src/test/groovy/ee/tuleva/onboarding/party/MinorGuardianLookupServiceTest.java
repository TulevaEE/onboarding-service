package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.scheduling.annotation.Scheduled;

@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
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
  private final MinorGuardianLookupLock lock = mock(MinorGuardianLookupLock.class);
  private final SimpleLock heldLock = mock(SimpleLock.class);

  private MinorGuardianLookupService service;

  @BeforeEach
  void setUp() {
    given(lock.acquire()).willReturn(Optional.of(heldLock));
    service =
        new MinorGuardianLookupService(
            thirdPillarAnalytics,
            populationRegisterClient,
            repository,
            lock,
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

  @Test
  void refusesToStartWhileAnotherLookupHoldsTheLock() {
    holders(MINOR);
    given(lock.acquire()).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO))
        .isInstanceOf(MinorGuardianLookupAlreadyRunning.class);
    verify(populationRegisterClient, never()).fetchGuardians(anyString(), anyString());
  }

  @Test
  void releasesTheLockWhenTheRunEndsEvenIfTheRegisterIsUnavailable() {
    holders(MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willThrow(new PopulationRegisterUnavailable("down", new RuntimeException()));

    service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    verify(heldLock).unlock();
  }

  @Test
  void logsNoPersonalCodeOfTheChildrenOrTheRequester(CapturedOutput output) {
    holders(MINOR, OTHER_MINOR);
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willThrow(new PopulationRegisterException("no person"));
    given(populationRegisterClient.fetchGuardians(REQUESTER, OTHER_MINOR))
        .willThrow(new PopulationRegisterUnavailable("down", new RuntimeException()));

    service.lookUpMinorThirdPillarHolders(REQUESTER, 10, ZERO);

    assertThat(output.getAll()).doesNotContain(MINOR, OTHER_MINOR, REQUESTER);
  }

  @Test
  void runsOnlyWhenStartedByHandNeverOnASchedule() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((reader, factory) -> true);

    assertThat(scanner.findCandidateComponents("ee.tuleva.onboarding.party"))
        .extracting(BeanDefinition::getBeanClassName)
        .map(Class::forName)
        .flatExtracting(type -> List.of(type.getDeclaredMethods()))
        .noneMatch(method -> method.isAnnotationPresent(Scheduled.class));
  }

  @Test
  void looksUpOneChildAgainReplacingWhatTheRegisterSaidBefore() {
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(
            new PopulationRegisterResult<>(List.of(guardian(MOTHER, PROPERTY_CUSTODY)), MESSAGE_ID))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(guardian(FATHER, PROPERTY_CUSTODY), guardian(FATHER, PERSONAL_CUSTODY)),
                MESSAGE_ID));

    service.lookUpChild(REQUESTER, MINOR);
    var guardians = service.lookUpChild(REQUESTER, MINOR);

    assertThat(guardians).isEqualTo(new ChildGuardianLookup(1));
    assertThat(repository.findById(MINOR).orElseThrow().getGuardians())
        .containsExactlyInAnyOrder(
            new LookedUpGuardian(FATHER, PROPERTY_CUSTODY),
            new LookedUpGuardian(FATHER, PERSONAL_CUSTODY));
    verify(heldLock, times(2)).unlock();
  }

  @Test
  void refusesToLookUpSomeoneWhoIsNotAMinor() {
    assertThatThrownBy(() -> service.lookUpChild(REQUESTER, TURNED_ADULT))
        .isInstanceOf(NotAMinor.class);
    verify(populationRegisterClient, never()).fetchGuardians(anyString(), anyString());
  }

  @Test
  void refusesAChildLookupWhileABatchHoldsTheLock() {
    given(lock.acquire()).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.lookUpChild(REQUESTER, MINOR))
        .isInstanceOf(MinorGuardianLookupAlreadyRunning.class);
    verify(populationRegisterClient, never()).fetchGuardians(anyString(), anyString());
  }

  @Test
  void looksUpAChildOnceOnlyWhenAskedAtAnEvent() {
    given(populationRegisterClient.fetchGuardians(REQUESTER, MINOR))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(guardian(MOTHER, PROPERTY_CUSTODY)), MESSAGE_ID));

    var first = service.lookUpChildOnce(REQUESTER, MINOR);
    var again = service.lookUpChildOnce(REQUESTER, MINOR);

    assertThat(first).contains(new ChildGuardianLookup(1));
    assertThat(again).isEmpty();
    verify(populationRegisterClient).fetchGuardians(REQUESTER, MINOR);
  }

  @Test
  void looksUpNoAdultAtAnEvent() {
    assertThat(service.lookUpChildOnce(REQUESTER, TURNED_ADULT)).isEmpty();
    verify(populationRegisterClient, never()).fetchGuardians(anyString(), anyString());
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
