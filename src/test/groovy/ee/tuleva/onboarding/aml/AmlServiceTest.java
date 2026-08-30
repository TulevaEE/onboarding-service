package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.*;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class AmlServiceTest {

  @Mock private AmlCheckRepository amlCheckRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UserConversionService userConversionService;

  @InjectMocks private AmlService amlService;

  @Captor private ArgumentCaptor<AmlCheck> amlCheckCaptor;
  @Captor private ArgumentCaptor<TrackableEvent> trackableEventCaptor;

  private static final Instant FIXED_INSTANT = Instant.parse("2020-11-23T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private Instant aYearAgoFromTestClock;

  enum Checks {
    REQUIRED(true),
    NOT_REQUIRED(false);

    private final boolean checksRequired;

    Checks(boolean required) {
      this.checksRequired = required;
    }
  }

  private User createUser(String personalCode, String firstName, String lastName, Long id) {
    User user = mock(User.class);
    when(user.getPersonalCode()).thenReturn(personalCode);
    when(user.getFirstName()).thenReturn(firstName);
    when(user.getLastName()).thenReturn(lastName);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private ContactDetails createContactDetails(
      String personalCode, String firstName, String lastName) {
    ContactDetails contactDetails = mock(ContactDetails.class);
    when(contactDetails.getPersonalCode()).thenReturn(personalCode);
    when(contactDetails.getFirstName()).thenReturn(firstName);
    when(contactDetails.getLastName()).thenReturn(lastName);
    return contactDetails;
  }

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
    aYearAgoFromTestClock = FIXED_INSTANT.minus(365, ChronoUnit.DAYS);
    lenient()
        .when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void addKycCheck_persistsFreshPassingCheckWhenOnlyFailedCheckExists() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    "38888888888", KYC_CHECK, aYearAgoFromTestClock))
        .willReturn(Optional.of(kycCheck(false, FIXED_INSTANT.minus(30, ChronoUnit.DAYS))));

    var result =
        amlService.addKycCheck("38888888888", new KycCheck(LOW, Map.of("riskLevel", "LOW")));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isTrue();
              assertThat(check.getMetadata()).isEqualTo(Map.of("riskLevel", "LOW"));
            });
  }

  @Test
  void addKycCheck_persistsPassingRecheckWhenLatestCheckFailedDespiteEarlierSuccess() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    "38888888888", KYC_CHECK, aYearAgoFromTestClock))
        .willReturn(Optional.of(kycCheck(false, FIXED_INSTANT.minus(90, ChronoUnit.DAYS))));

    var result =
        amlService.addKycCheck("38888888888", new KycCheck(LOW, Map.of("riskLevel", "LOW")));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isTrue();
              assertThat(check.getMetadata()).isEqualTo(Map.of("riskLevel", "LOW"));
            });
  }

  @Test
  void addCustodyRightCheck_persistsCheckWithMetadata() {
    var metadata = Map.<String, Object>of("custodyType", "PROPERTY", "childAlive", true);

    AmlCheck result = amlService.addCustodyRightCheck("61506150006", true, metadata);

    assertThat(result.getPersonalCode()).isEqualTo("61506150006");
    assertThat(result.getType()).isEqualTo(CUSTODY_RIGHT);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMetadata()).isEqualTo(metadata);
  }

  @Test
  void addKycCheck_persistsStillFailingRecheckWhenNoSuccessfulCheckExists() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    "38888888888", KYC_CHECK, aYearAgoFromTestClock))
        .willReturn(Optional.of(kycCheck(false, FIXED_INSTANT.minus(30, ChronoUnit.DAYS))));

    var result =
        amlService.addKycCheck("38888888888", new KycCheck(HIGH, Map.of("riskLevel", "HIGH")));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isFalse();
            });
  }

  @Test
  void addKycCheck_skipsPassingRecheckWhenRecentSuccessfulCheckExists() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    "38888888888", KYC_CHECK, aYearAgoFromTestClock))
        .willReturn(Optional.of(kycCheck(true, FIXED_INSTANT.minus(30, ChronoUnit.DAYS))));

    var result =
        amlService.addKycCheck("38888888888", new KycCheck(LOW, Map.of("riskLevel", "LOW")));

    assertThat(result).isEmpty();
    verify(amlCheckRepository, never()).save(any(AmlCheck.class));
  }

  @Test
  void addKycCheck_persistsAdverseRecheckEvenWhenRecentSuccessfulCheckExists() {
    given(
            amlCheckRepository
                .findFirstByPersonalCodeAndTypeAndCreatedTimeAfterOrderByCreatedTimeDescIdDesc(
                    "38888888888", KYC_CHECK, aYearAgoFromTestClock))
        .willReturn(Optional.of(kycCheck(true, FIXED_INSTANT.minus(30, ChronoUnit.DAYS))));

    var result =
        amlService.addKycCheck("38888888888", new KycCheck(HIGH, Map.of("riskLevel", "HIGH")));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isFalse();
            });
  }

  private AmlCheck kycCheck(boolean success, Instant createdTime) {
    return AmlCheck.builder()
        .personalCode("38888888888")
        .type(KYC_CHECK)
        .success(success)
        .createdTime(createdTime)
        .build();
  }

  @Test
  void checkUserBeforeLogin_addsAllChecks_whenResident() {
    // given
    User testUser = createUser("12345", "Test", "User", 1L);
    Boolean isResident = true;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(testUser, testUser, isResident);

    // then
    verify(amlCheckRepository, times(3)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();

    assertTrue(
        savedChecks.stream()
            .anyMatch(
                c ->
                    c.getType() == DOCUMENT
                        && c.isSuccess()
                        && c.getPersonalCode().equals("12345")));
    assertTrue(
        savedChecks.stream()
            .anyMatch(
                c ->
                    c.getType() == RESIDENCY_AUTO
                        && c.isSuccess()
                        && c.getPersonalCode().equals("12345")));
    AmlCheck skNameCheck =
        savedChecks.stream().filter(c -> c.getType() == SK_NAME).findFirst().orElseThrow();
    assertTrue(skNameCheck.isSuccess());
    assertEquals("12345", skNameCheck.getPersonalCode());
    assertNotNull(skNameCheck.getMetadata());
    assertEquals(new PersonImpl(testUser), skNameCheck.getMetadata().get("user"));
    assertEquals(new PersonImpl(testUser), skNameCheck.getMetadata().get("person"));

    verify(eventPublisher, times(3)).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void checkUserBeforeLogin_noResidencyCheck_whenIsNull() {
    // given
    User testUser = createUser("12345", "Test", "User", 1L);
    Boolean isResident = null;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(testUser, testUser, isResident);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();
    assertTrue(savedChecks.stream().anyMatch(c -> c.getType() == DOCUMENT));
    assertTrue(savedChecks.stream().anyMatch(c -> c.getType() == SK_NAME));
    assertFalse(savedChecks.stream().anyMatch(c -> c.getType() == RESIDENCY_AUTO));
  }

  @Test
  void checkUserBeforeLogin_skNameCheckFails_onLastNameMismatch() {
    // given
    User user = createUser("12345", "Test", "User", 1L);
    Person person = createUser("12345", "Test", "DifferentUser", 1L);
    Boolean isResident = true;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(user, person, isResident);

    // then
    verify(amlCheckRepository, times(3)).save(amlCheckCaptor.capture());
    AmlCheck skNameCheck =
        amlCheckCaptor.getAllValues().stream()
            .filter(c -> c.getType() == SK_NAME)
            .findFirst()
            .orElseThrow();
    assertFalse(skNameCheck.isSuccess(), "SK Name check should fail due to last name mismatch");
  }

  @Test
  void addPensionRegistryNameCheckIfMissing_addsCheck_whenMissingAndNamesMatch() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    ContactDetails contactDetails = createContactDetails("123", "First", "Last");
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", PENSION_REGISTRY_NAME, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result =
        amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(PENSION_REGISTRY_NAME, savedCheck.getType());
    assertTrue(savedCheck.isSuccess());
    assertEquals("123", savedCheck.getPersonalCode());
    assertEquals(new PersonImpl(user), savedCheck.getMetadata().get("user"));
    assertEquals(new PersonImpl(contactDetails), savedCheck.getMetadata().get("person"));
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void addPensionRegistryNameCheckIfMissing_savesFailedCheck_onLastNameMismatch() {
    // given
    User user = createUser("123", "First", "OriginalLast", 1L);
    ContactDetails contactDetails = createContactDetails("123", "First", "DifferentLast");
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", PENSION_REGISTRY_NAME, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result =
        amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(PENSION_REGISTRY_NAME, savedCheck.getType());
    assertFalse(savedCheck.isSuccess(), "Pension registry name check should fail");
  }

  @Test
  void addContactDetailsCheckIfMissing_addsCheck() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", CONTACT_DETAILS, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result = amlService.addContactDetailsCheckIfMissing(user);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(CONTACT_DETAILS, savedCheck.getType());
    assertTrue(savedCheck.isSuccess());
    assertEquals("123", savedCheck.getPersonalCode());
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void addCheckIfMissing_addsCheck_whenNotExists() {
    // given
    AmlCheck newCheck = AmlCheck.builder().personalCode("123").type(DOCUMENT).success(true).build();
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", DOCUMENT, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result = amlService.addCheckIfMissing(newCheck);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(newCheck);
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void addCheckIfMissing_doesNotAdd_whenExists() {
    // given
    AmlCheck existingCheck =
        AmlCheck.builder().personalCode("123").type(DOCUMENT).success(true).build();
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", DOCUMENT, aYearAgoFromTestClock))
        .thenReturn(true);

    // when
    Optional<AmlCheck> result = amlService.addCheckIfMissing(existingCheck);

    // then
    assertFalse(result.isPresent());
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  void getChecks_returnsAllChecks() {
    // given
    User user = createUser("123", "Test", "User", 1L);
    List<AmlCheck> expectedChecks = List.of(AmlCheck.builder().build());
    when(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter("123", aYearAgoFromTestClock))
        .thenReturn(expectedChecks);

    // when
    List<AmlCheck> actualChecks = amlService.getChecks(user);

    // then
    assertEquals(expectedChecks, actualChecks);
  }

  @Test
  void allChecksPassed_trueForPillar2() {
    // given
    User user = createUser("123", "Test", "User", 1L);
    var mandate = sampleMandate();

    assertEquals(2, mandate.getPillar());

    // when
    boolean result = amlService.allChecksPassed(user, mandate);

    // then
    assertTrue(result);
  }

  private static Stream<Arguments> allChecksPassedThirdPillarScenarios() {
    return Stream.of(
        Arguments.of(List.of(), false, true),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                SK_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_MANUAL,
                PENSION_REGISTRY_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_AUTO,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                SK_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_OVERRIDE,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_MANUAL,
                PENSION_REGISTRY_NAME),
            true,
            false),
        Arguments.of(
            Stream.concat(
                    successfulChecks(SANCTION, DOCUMENT, OCCUPATION, RESIDENCY_AUTO, SK_NAME)
                        .stream(),
                    failedChecks(POLITICALLY_EXPOSED_PERSON).stream())
                .toList(),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON, DOCUMENT, OCCUPATION, RESIDENCY_AUTO, SK_NAME),
            false,
            true),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON, SANCTION, DOCUMENT, OCCUPATION, RESIDENCY_MANUAL),
            false,
            true),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_AUTO,
                SANCTION_OVERRIDE,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                PENSION_REGISTRY_NAME),
            true,
            false));
  }

  @ParameterizedTest
  @MethodSource("allChecksPassedThirdPillarScenarios")
  void allChecksPassed_evaluatesThirdPillar(
      List<AmlCheck> checks, boolean expectedResult, boolean eventExpected) {
    // given
    User user = createUser("12345", "Test", "User", 1L);
    var mandate = thirdPillarMandate();
    assertEquals(3, mandate.getPillar());
    when(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), aYearAgoFromTestClock))
        .thenReturn(checks);

    when(userConversionService.getConversion(user)).thenReturn(fullyConverted());

    // when
    boolean actualResult = amlService.allChecksPassed(user, mandate);

    // then
    assertEquals(expectedResult, actualResult);
    if (eventExpected) {
      verify(eventPublisher).publishEvent(trackableEventCaptor.capture());
    } else {
      verify(eventPublisher, never()).publishEvent(any(TrackableEvent.class));
    }
  }

  private static Stream<Arguments> amlChecksRequiredMandateScenarios() {
    var tulevaClientScenarios =
        Stream.of(
            Arguments.of(fullyConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(notFullyConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(fullyConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(notFullyConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(
                fullyConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.REQUIRED),
            Arguments.of(
                notFullyConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.REQUIRED),
            Arguments.of(
                fullyConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED),
            Arguments.of(
                notFullyConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED));

    var notTulevaClientScenarios =
        Stream.of(
            Arguments.of(notConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(notConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(
                notConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED),
            Arguments.of(
                notConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED));

    return Stream.concat(tulevaClientScenarios, notTulevaClientScenarios);
  }

  @ParameterizedTest
  @MethodSource("amlChecksRequiredMandateScenarios")
  void isMandateAmlCheckRequired_worksCorrectly(
      ConversionResponse conversion, Mandate mandate, Checks expectedResult) {
    User user = createUser("12345", "Test", "User", 1L);
    when(userConversionService.getConversion(user)).thenReturn(conversion);

    boolean actualResult = amlService.isMandateAmlCheckRequired(user, mandate);

    assertEquals(expectedResult.checksRequired, actualResult);
  }

  private static List<AmlCheck> successfulChecks(AmlCheckType... checkTypes) {
    return stream(checkTypes)
        .map(type -> AmlCheck.builder().type(type).success(true).build())
        .toList();
  }

  private static List<AmlCheck> failedChecks(AmlCheckType... checkTypes) {
    return stream(checkTypes)
        .map(type -> AmlCheck.builder().type(type).success(false).build())
        .toList();
  }
}
