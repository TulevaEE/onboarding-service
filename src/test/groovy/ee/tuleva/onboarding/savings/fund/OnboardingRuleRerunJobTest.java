package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.OnboardingRuleRerunJob.REJECTED_BY_THE_EARLIER_RULE_BEFORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingRuleRerunJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private UserService userService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private KycCheckService kycCheckService;

  private final User rejected = sampleUser().id(1L).personalCode("38888888888").build();
  private final User otherRejected = sampleUser().id(2L).personalCode("38888888899").build();
  private final User pending = sampleUser().id(3L).personalCode("48888888888").build();

  @Test
  void holdsTheEarlierRulesRejectionsAndAssessesThePendingSurveysAgainOnItsEvening() {
    given(
            savingsFundOnboardingRepository.findPersonCodes(
                REJECTED, REJECTED_BY_THE_EARLIER_RULE_BEFORE))
        .willReturn(List.of(rejected.getPersonalCode(), otherRejected.getPersonalCode()));
    given(savingsFundOnboardingRepository.findPersonCodes(PENDING, at("2026-11-18T20:00:00")))
        .willReturn(List.of(pending.getPersonalCode()));
    given(userService.findByPersonalCode(rejected.getPersonalCode()))
        .willReturn(Optional.of(rejected));
    given(userService.findByPersonalCode(otherRejected.getPersonalCode()))
        .willReturn(Optional.of(otherRejected));
    given(userService.findByPersonalCode(pending.getPersonalCode()))
        .willReturn(Optional.of(pending));
    willThrow(new IllegalStateException("write failed"))
        .given(savingsFundOnboardingService)
        .holdForReview(rejected, REJECTED_BY_THE_EARLIER_RULE_BEFORE);

    job("2026-11-18T20:00:00").rerun();

    then(savingsFundOnboardingService)
        .should()
        .holdForReview(otherRejected, REJECTED_BY_THE_EARLIER_RULE_BEFORE);
    then(kycCheckService).should().checkLatestOnboardingSurvey(pending);
  }

  @Test
  void skipsAPersonWithoutAUser() {
    given(
            savingsFundOnboardingRepository.findPersonCodes(
                REJECTED, REJECTED_BY_THE_EARLIER_RULE_BEFORE))
        .willReturn(List.of(rejected.getPersonalCode()));
    given(savingsFundOnboardingRepository.findPersonCodes(PENDING, at("2026-11-18T20:00:00")))
        .willReturn(List.of());
    given(userService.findByPersonalCode(rejected.getPersonalCode())).willReturn(Optional.empty());

    job("2026-11-18T20:00:00").rerun();

    then(savingsFundOnboardingService).shouldHaveNoInteractions();
  }

  @Test
  void countsTheEarlierRulesRejectionsUntilTheStartOfTheSeventeenthInTallinn() {
    assertThat(REJECTED_BY_THE_EARLIER_RULE_BEFORE)
        .isEqualTo(LocalDate.of(2026, 11, 17).atStartOfDay(TALLINN).toInstant());
  }

  @Test
  void doesNothingOnAnyOtherDay() {
    job("2026-11-19T20:00:00").rerun();
    job("2026-11-17T20:00:00").rerun();

    then(savingsFundOnboardingRepository).shouldHaveNoInteractions();
    then(savingsFundOnboardingService).shouldHaveNoInteractions();
    then(kycCheckService).shouldHaveNoInteractions();
  }

  private OnboardingRuleRerunJob job(String tallinnTime) {
    return new OnboardingRuleRerunJob(
        savingsFundOnboardingRepository,
        userService,
        savingsFundOnboardingService,
        kycCheckService,
        Clock.fixed(at(tallinnTime), ZoneOffset.UTC));
  }

  private static Instant at(String tallinnTime) {
    return LocalDateTime.parse(tallinnTime).atZone(TALLINN).toInstant();
  }
}
