package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.nudge.ExperimentArm.CONTROL;
import static ee.tuleva.onboarding.nudge.ExperimentArm.TREATMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRateRedirectServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final String SEED = "season-2026-test-seed";
  private static final String KEY = NudgeKey.SECOND_PILLAR_PAYMENT_RATE.name();
  private static final LocalDate START_DATE = LocalDate.of(2026, 9, 1);

  @Mock private PaymentRateRedirectEligibility eligibility;
  @Mock private NudgeExposureRepository nudgeExposureRepository;
  @Mock private UserService userService;

  private final User user = sampleUser().build();

  @BeforeEach
  void setUp() {
    lenient().when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
  }

  private static AuthenticatedPerson person(String personalCode) {
    return AuthenticatedPerson.builder()
        .firstName("Jordan")
        .lastName("Valdma")
        .personalCode(personalCode)
        .userId(sampleUser().build().getId())
        .role(null)
        .build();
  }

  private static AuthenticatedPerson company(String personalCode) {
    return AuthenticatedPerson.builder()
        .firstName("Jordan")
        .lastName("Valdma")
        .personalCode(personalCode)
        .userId(sampleUser().build().getId())
        .role(new Role(RoleType.LEGAL_ENTITY, "12345678", "Acme OU"))
        .build();
  }

  private static AuthenticatedPerson parentActingForChild(String personalCode) {
    return AuthenticatedPerson.builder()
        .firstName("Jordan")
        .lastName("Valdma")
        .personalCode(personalCode)
        .userId(sampleUser().build().getId())
        .role(new Role(RoleType.PERSON, "38812121215", "Child"))
        .build();
  }

  private PaymentRateRedirectService serviceOn(String date) {
    return serviceOn(date, properties(true));
  }

  private PaymentRateRedirectService serviceOn(
      String date, PaymentRateRedirectProperties properties) {
    Clock clock =
        Clock.fixed(LocalDateTime.parse(date + "T09:00:00").atZone(TALLINN).toInstant(), TALLINN);
    return new PaymentRateRedirectService(
        properties,
        eligibility,
        nudgeExposureRepository,
        userService,
        new PaymentRateSeasons(clock, new MandateDeadlinesService(clock, new PublicHolidays())),
        clock);
  }

  private static PaymentRateRedirectProperties properties(boolean enabled) {
    return new PaymentRateRedirectProperties(enabled, SEED, 20, new BigDecimal("3300"), START_DATE);
  }

  @Test
  void anEligibleTreatmentPersonIsSentToTheRatePageOnceForTheSeason() {
    given(eligibility.isEligible(user)).willReturn(true);
    given(
            nudgeExposureRepository.recordAssignment(
                user.getId(), KEY, 2026, TREATMENT, Instant.parse("2026-09-15T06:00:00Z")))
        .willReturn(true);

    assertThat(serviceOn("2026-09-15").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.to(TREATMENT, 2026));
  }

  @Test
  void aSecondRequestInTheSameSeasonIsRefusedBecauseTheExposureIsAlreadyRecorded() {
    given(eligibility.isEligible(user)).willReturn(true);
    given(
            nudgeExposureRepository.recordAssignment(
                user.getId(), KEY, 2026, TREATMENT, Instant.parse("2026-09-15T06:00:00Z")))
        .willReturn(false);

    assertThat(serviceOn("2026-09-15").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());
  }

  @Test
  void aControlPersonIsRecordedButNeverSentAnywhere() {
    given(eligibility.isEligible(user)).willReturn(true);
    given(
            nudgeExposureRepository.recordAssignment(
                user.getId(), KEY, 2026, CONTROL, Instant.parse("2026-09-15T06:00:00Z")))
        .willReturn(true);

    assertThat(serviceOn("2026-09-15").assign(person("38888880068")))
        .isEqualTo(PaymentRateRedirect.no());
  }

  @Test
  void anIneligiblePersonIsNeitherRecordedNorSent() {
    given(eligibility.isEligible(user)).willReturn(false);

    assertThat(serviceOn("2026-09-15").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());
    verifyNoInteractions(nudgeExposureRepository);
  }

  @Test
  void theKillSwitchStopsEverythingBeforeAnyLookup() {
    assertThat(serviceOn("2026-09-15", properties(false)).assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());

    verifyNoInteractions(eligibility, nudgeExposureRepository, userService);
  }

  @Test
  void aParentActingForAChildIsNeverLookedUpNorRecorded() {
    PaymentRateRedirectService service = serviceOn("2026-09-15");

    assertThat(service.assign(parentActingForChild("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());
    service.dismiss(parentActingForChild("38888880000"));

    verifyNoInteractions(eligibility, nudgeExposureRepository, userService);
  }

  @Test
  void aPersonActingForACompanyIsNeverLookedUp() {
    assertThat(serviceOn("2026-09-15").assign(company("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());

    verifyNoInteractions(eligibility, nudgeExposureRepository, userService);
  }

  @Test
  void beforeTheStartDateNothingIsLookedUp() {
    assertThat(serviceOn("2026-08-31").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());

    verifyNoInteractions(eligibility, nudgeExposureRepository, userService);
  }

  @Test
  void theWindowRunsToTheEndOfTheDeadlineDayAndNotAnHourLonger() {
    given(eligibility.isEligible(user)).willReturn(true);
    given(
            nudgeExposureRepository.recordAssignment(
                user.getId(), KEY, 2026, TREATMENT, Instant.parse("2026-11-30T07:00:00Z")))
        .willReturn(true);

    assertThat(serviceOn("2026-11-30").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.to(TREATMENT, 2026));
    assertThat(serviceOn("2026-12-01").assign(person("38888880000")))
        .isEqualTo(PaymentRateRedirect.no());
  }

  @Test
  void aDismissalIsStampedOnTheSeasonTheWindowBelongsTo() {
    serviceOn("2026-09-15").dismiss(person("38888880000"));

    verify(nudgeExposureRepository)
        .recordDismissal(user.getId(), KEY, 2026, Instant.parse("2026-09-15T06:00:00Z"));
  }
}
