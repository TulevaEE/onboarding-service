package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlChecksRunEvent;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class AmlBatchScreenerTest {

  @Mock private AmlCheckRepository amlCheckRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PepAndSanctionCheckService pepAndSanctionCheckService;
  @Mock private ThirdPillarAnalytics thirdPillarAnalytics;
  @Mock private SavingsFundCustomers savingsFundCustomers;
  @Mock private UserRepository userRepository;
  @Mock private KycCountryService kycCountryService;
  @Mock private OperationsNotificationService notificationService;
  @Spy private JsonMapper jsonMapper = JsonMapper.builder().build();
  @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private static final JsonMapper objectMapper = JsonMapper.builder().build();

  private AmlBatchScreener amlBatchScreener;

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-05T03:30:00Z"), UTC));
    AmlService amlService =
        new AmlService(amlCheckRepository, eventPublisher, mock(UserConversionService.class));
    SanctionAndPepScreener sanctionAndPepScreener =
        new SanctionAndPepScreener(
            amlCheckRepository,
            pepAndSanctionCheckService,
            kycCountryService,
            jsonMapper,
            meterRegistry,
            amlService);
    amlBatchScreener =
        new AmlBatchScreener(
            eventPublisher,
            thirdPillarAnalytics,
            savingsFundCustomers,
            userRepository,
            notificationService,
            sanctionAndPepScreener,
            amlCheckRepository);
    lenient()
        .when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private User savingsFundCustomer(String personalCode, Long id) {
    return User.builder()
        .id(id)
        .personalCode(personalCode)
        .firstName("First" + personalCode)
        .lastName("Last" + personalCode)
        .build();
  }

  private void givenRecordedCitizenships(String personalCode, List<String> citizenships) {
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                personalCode, AmlCheckType.CUSTODY_RIGHT))
        .willReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode(personalCode)
                    .type(AmlCheckType.CUSTODY_RIGHT)
                    .success(true)
                    .metadata(Map.of("citizenships", citizenships))
                    .build()));
  }

  private ThirdPillarHolder thirdPillarRecord(String personalCode, String country) {
    return new ThirdPillarHolder(personalCode, "First", "Last", country);
  }

  @Test
  void runAmlChecksOnThirdPillarCustomers_sendsSingleAggregatedAlertOnScreeningFailures() {
    // given
    ThirdPillarHolder ok = thirdPillarRecord("ok", "EE");
    ThirdPillarHolder fail1 = thirdPillarRecord("fail1", "EE");
    ThirdPillarHolder fail2 = thirdPillarRecord("fail2", "EE");
    when(thirdPillarAnalytics.holdersInLatestSnapshot()).thenReturn(List.of(fail1, ok, fail2));

    MatchResponse emptyMatchResponse =
        new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(eq(ok), anySet())).thenReturn(emptyMatchResponse);
    when(pepAndSanctionCheckService.match(eq(fail1), anySet()))
        .thenThrow(new RuntimeException("Match service error"));
    when(pepAndSanctionCheckService.match(eq(fail2), anySet()))
        .thenThrow(new RuntimeException("Match service error"));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlBatchScreener.runAmlChecksOnThirdPillarCustomers();

    // then
    verify(notificationService, times(1))
        .sendMessage(
            "AML batch: sanction/PEP screening failed for 2 of 3 third-pillar customers this run",
            OperationsNotificationService.Channel.AML);
    verify(amlCheckRepository, times(2)).save(any(AmlCheck.class));
    assertEquals(
        2.0,
        meterRegistry.counter("aml.screening.failure", "phase", "match").count(),
        "Both screening failures should increment the metric");
  }

  @Test
  void runAmlChecksOnThirdPillarCustomers_slackFailureDoesNotAbortBatch() {
    // given
    ThirdPillarHolder c1 = thirdPillarRecord("c1", "EE");
    ThirdPillarHolder c2 = thirdPillarRecord("c2", "EE");
    when(thirdPillarAnalytics.holdersInLatestSnapshot()).thenReturn(List.of(c1, c2));
    when(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .thenThrow(new RuntimeException("Match service error"));
    doThrow(new RuntimeException("Slack is down"))
        .when(notificationService)
        .sendMessage(anyString(), any());

    // when / then
    assertDoesNotThrow(() -> amlBatchScreener.runAmlChecksOnThirdPillarCustomers());

    assertEquals(
        2.0,
        meterRegistry.counter("aml.screening.failure", "phase", "match").count(),
        "Both customers should have been screened");
  }

  @Test
  void runAmlChecksOnThirdPillarCustomers_processesRecords() {
    // given
    ThirdPillarHolder record1 = new ThirdPillarHolder("p1", "F1", "L1", "EE");
    when(thirdPillarAnalytics.holdersInLatestSnapshot()).thenReturn(List.of(record1));

    MatchResponse emptyMatchResponse =
        new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(eq(record1), anySet())).thenReturn(emptyMatchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            anyString(), eq(AmlCheckType.POLITICALLY_EXPOSED_PERSON_OVERRIDE), eq(true)))
        .thenReturn(List.of());
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            anyString(), eq(AmlCheckType.SANCTION_OVERRIDE), eq(true)))
        .thenReturn(List.of());

    // when
    amlBatchScreener.runAmlChecksOnThirdPillarCustomers();

    // then
    verify(eventPublisher).publishEvent(any(AmlChecksRunEvent.class));

    verify(pepAndSanctionCheckService).match(record1, Countries.of("EE"));
    verify(amlCheckRepository, times(2)).save(any(AmlCheck.class));
    verify(eventPublisher, times(2)).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_skipsOnlyHoldersWithBothChecks() {
    ThirdPillarHolder screened = thirdPillarRecord("screened", "EE");
    ThirdPillarHolder sanctionOnly = thirdPillarRecord("sanctionOnly", "EE");
    ThirdPillarHolder joiner = thirdPillarRecord("joiner", "LV");
    givenTodaysSnapshotWith(screened, sanctionOnly, joiner);
    givenScreenedThisYear(SANCTION, "screened", "sanctionOnly");
    givenScreenedThisYear(POLITICALLY_EXPOSED_PERSON_AUTO, "screened");
    given(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .willReturn(emptyMatchResponse());

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(pepAndSanctionCheckService).match(joiner, Countries.of("LV"));
    verify(pepAndSanctionCheckService).match(sanctionOnly, Countries.of("EE"));
    verify(pepAndSanctionCheckService, never()).match(eq(screened), anySet());
  }

  @Test
  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_postsSnapshotDateAndCounts() {
    ThirdPillarHolder screened = thirdPillarRecord("screened", "EE");
    ThirdPillarHolder joiner = thirdPillarRecord("joiner", "EE");
    ThirdPillarHolder failing = thirdPillarRecord("failing", "EE");
    givenTodaysSnapshotWith(screened, joiner, failing);
    givenScreenedThisYear(SANCTION, "screened");
    givenScreenedThisYear(POLITICALLY_EXPOSED_PERSON_AUTO, "screened");
    given(pepAndSanctionCheckService.match(eq(joiner), anySet())).willReturn(emptyMatchResponse());
    given(pepAndSanctionCheckService.match(eq(failing), anySet()))
        .willThrow(new RuntimeException("Match service error"));

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(notificationService)
        .sendMessage(
            "AML batch: sanction/PEP screening failed for 1 of 2 third-pillar customers this run",
            AML);
    verify(notificationService)
        .sendMessage(
            "III sammas (registri seis 05.11.2026): viimase 12 kuu jooksul kontrollimata 2,"
                + " kontrollitud 1, tõrkeid 1",
            AML);
  }

  @Test
  void
      runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_reportsZeroWhenEveryHolderIsScreened() {
    givenTodaysSnapshotWith(thirdPillarRecord("screened", "EE"));
    givenScreenedThisYear(SANCTION, "screened");
    givenScreenedThisYear(POLITICALLY_EXPOSED_PERSON_AUTO, "screened");

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verifyNoInteractions(pepAndSanctionCheckService);
    verify(notificationService)
        .sendMessage(
            "III sammas (registri seis 05.11.2026): viimase 12 kuu jooksul kontrollimata 0,"
                + " kontrollitud 0, tõrkeid 0",
            AML);
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_flagsASnapshotOlderThanToday() {
    given(thirdPillarAnalytics.latestSnapshotDate()).willReturn(Optional.of(TODAY.minusDays(1)));
    given(thirdPillarAnalytics.holdersInLatestSnapshot()).willReturn(List.of());

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(notificationService)
        .sendMessage(
            "III sammas (registri seis 04.11.2026, ei ole tänane): viimase 12 kuu jooksul"
                + " kontrollimata 0, kontrollitud 0, tõrkeid 0",
            AML);
  }

  @Test
  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_postsAFailureLineWhenItCannotRun() {
    given(thirdPillarAnalytics.latestSnapshotDate()).willReturn(Optional.of(TODAY));
    given(thirdPillarAnalytics.holdersInLatestSnapshot())
        .willThrow(new IllegalStateException("database unavailable"));

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(notificationService)
        .sendMessage(
            "III sammas: igapäevane kontroll ebaõnnestus:"
                + " reason=java.lang.IllegalStateException: database unavailable",
            AML);
  }

  @Test
  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_postsAFailureLineWithoutASnapshot() {
    given(thirdPillarAnalytics.latestSnapshotDate()).willReturn(Optional.empty());

    amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening();

    verify(notificationService)
        .sendMessage(
            "III sammas: igapäevane kontroll ebaõnnestus:"
                + " reason=java.lang.IllegalStateException: No unit owner snapshot",
            AML);
  }

  @Test
  void
      runAmlChecksOnThirdPillarHoldersWithoutRecentScreening_slackFailureOnTheSummaryDoesNotThrow() {
    givenTodaysSnapshotWith();
    doThrow(new RuntimeException("Slack is down"))
        .when(notificationService)
        .sendMessage(anyString(), any());

    assertDoesNotThrow(
        () -> amlBatchScreener.runAmlChecksOnThirdPillarHoldersWithoutRecentScreening());
    verify(notificationService, times(1)).sendMessage(anyString(), any());
  }

  private void givenTodaysSnapshotWith(ThirdPillarHolder... holders) {
    given(thirdPillarAnalytics.latestSnapshotDate()).willReturn(Optional.of(TODAY));
    given(thirdPillarAnalytics.holdersInLatestSnapshot()).willReturn(List.of(holders));
  }

  private void givenScreenedThisYear(AmlCheckType type, String... personalCodes) {
    given(
            amlCheckRepository.findPersonalCodesByTypeAndCreatedTimeAfter(
                eq(type), any(Instant.class)))
        .willReturn(List.of(personalCodes));
  }

  private MatchResponse emptyMatchResponse() {
    return new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_screensEachCustomerOnEveryCountryTheyAreTiedTo() {
    User adult = savingsFundCustomer("38888888881", 1L);
    User child = savingsFundCustomer("61506150006", 2L);
    given(savingsFundCustomers.personalCodes())
        .willReturn(List.of(adult.getPersonalCode(), child.getPersonalCode()));
    given(
            userRepository.findAllByPersonalCodeIn(
                List.of(adult.getPersonalCode(), child.getPersonalCode())))
        .willReturn(List.of(adult, child));
    given(kycCountryService.getCountries(1L)).willReturn(Optional.of(Countries.of("EE", "RU")));
    given(kycCountryService.getCountries(2L)).willReturn(Optional.empty());
    givenRecordedCitizenships(child.getPersonalCode(), List.of("UA"));
    given(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .willReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));

    amlBatchScreener.runAmlChecksOnSavingsFundCustomers();

    verify(pepAndSanctionCheckService).match(adult, Countries.of("EE", "RU"));
    verify(pepAndSanctionCheckService).match(child, Countries.of("UA"));
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_screensEveryOnboardedPerson() {
    User first = savingsFundCustomer("38888888881", 11L);
    User second = savingsFundCustomer("38888888882", 12L);
    when(savingsFundCustomers.personalCodes())
        .thenReturn(List.of(first.getPersonalCode(), second.getPersonalCode()));
    when(userRepository.findAllByPersonalCodeIn(
            List.of(first.getPersonalCode(), second.getPersonalCode())))
        .thenReturn(List.of(first, second));
    when(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    amlBatchScreener.runAmlChecksOnSavingsFundCustomers();

    verify(pepAndSanctionCheckService).match(first, Countries.<String>of());
    verify(pepAndSanctionCheckService).match(second, Countries.<String>of());
    verify(notificationService, never()).sendMessage(anyString(), any());
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_sendsSingleAggregatedAlertOnScreeningFailures() {
    User ok = savingsFundCustomer("38888888881", 11L);
    User failing = savingsFundCustomer("38888888882", 12L);
    when(savingsFundCustomers.personalCodes())
        .thenReturn(List.of(ok.getPersonalCode(), failing.getPersonalCode()));
    when(userRepository.findAllByPersonalCodeIn(any())).thenReturn(List.of(ok, failing));
    when(pepAndSanctionCheckService.match(eq(ok), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(pepAndSanctionCheckService.match(eq(failing), anySet()))
        .thenThrow(new RuntimeException("Match service error"));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    amlBatchScreener.runAmlChecksOnSavingsFundCustomers();

    verify(notificationService, times(1))
        .sendMessage(
            "AML batch: sanction/PEP screening failed for 1 of 2 savings fund customers this run",
            OperationsNotificationService.Channel.AML);
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_countsCheckPersistenceFailuresAndContinues() {
    User failing = savingsFundCustomer("38888888881", 11L);
    when(savingsFundCustomers.personalCodes()).thenReturn(List.of(failing.getPersonalCode()));
    when(userRepository.findAllByPersonalCodeIn(any())).thenReturn(List.of(failing));
    when(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenThrow(new RuntimeException("Database unavailable"));

    assertDoesNotThrow(() -> amlBatchScreener.runAmlChecksOnSavingsFundCustomers());

    verify(notificationService)
        .sendMessage(
            "AML batch: sanction/PEP screening failed for 1 of 1 savings fund customers this run",
            OperationsNotificationService.Channel.AML);
    assertEquals(
        1.0, meterRegistry.counter("aml.screening.failure", "phase", "savings-fund-batch").count());
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_slackFailureDoesNotAbortBatch() {
    User failing = savingsFundCustomer("38888888881", 11L);
    when(savingsFundCustomers.personalCodes()).thenReturn(List.of(failing.getPersonalCode()));
    when(userRepository.findAllByPersonalCodeIn(any())).thenReturn(List.of(failing));
    when(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .thenThrow(new RuntimeException("Match service error"));
    doThrow(new RuntimeException("Slack down"))
        .when(notificationService)
        .sendMessage(anyString(), any());

    assertDoesNotThrow(() -> amlBatchScreener.runAmlChecksOnSavingsFundCustomers());
  }

  @Test
  void runAmlChecksOnSavingsFundCustomers_doesNothingWhenNobodyIsOnboarded() {
    when(savingsFundCustomers.personalCodes()).thenReturn(List.of());
    when(userRepository.findAllByPersonalCodeIn(List.of())).thenReturn(List.of());

    amlBatchScreener.runAmlChecksOnSavingsFundCustomers();

    verify(pepAndSanctionCheckService, never()).match(any(Person.class), anySet());
    verify(notificationService, never()).sendMessage(anyString(), any());
  }
}
