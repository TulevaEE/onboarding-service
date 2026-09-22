package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.nudge.NudgeContext.MEMBERSHIP;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineNudgeInputsTest {

  @Mock private PensionRegistry pensionRegistry;
  @Mock private SecondPillarLeaverStatus leaverStatus;
  @Mock private RecurringContributionStatus recurringStatus;
  @Mock private SavingsFundSaverStatus saverStatus;
  @Mock private TaxHeadroom taxHeadroom;
  @Mock private ActingParties actingParties;
  @Mock private SavingsFundFeeRate savingsFundFeeRate;

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final User user = sampleUser().build();
  private OfflineNudgeInputs offlineInputs;

  private OfflineNudgeInputs offlineInputsOn(String date) {
    Clock clock =
        Clock.fixed(LocalDateTime.parse(date + "T09:00:00").atZone(TALLINN).toInstant(), TALLINN);
    return new OfflineNudgeInputs(
        pensionRegistry,
        new KnownLookups(
            leaverStatus,
            recurringStatus,
            saverStatus,
            taxHeadroom,
            actingParties,
            savingsFundFeeRate),
        new PaymentRateSeasons(clock, new MandateDeadlinesService(clock, new PublicHolidays())));
  }

  @BeforeEach
  void setUp() {
    offlineInputs = offlineInputsOn("2026-09-10");
    given(savingsFundFeeRate.ongoingChargesPercent()).willReturn(new BigDecimal("0.28"));
    given(recurringStatus.savingsFund(any())).willReturn(true);
    given(saverStatus.savesFor(any())).willReturn(true);
  }

  @Test
  void readsThePensionFactsFromTheRegistrySnapshotAndLeavesEpisOnlyFactsUnknown() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(true, true, false, true, true)));
    given(recurringStatus.thirdPillar(user.getPersonalCode())).willReturn(false);

    NudgeInputs inputs = offlineInputs.assemble(OfflineSaver.of(user), MEMBERSHIP);

    assertThat(inputs.secondPillarActive()).isTrue();
    assertThat(inputs.secondPillarPartiallyConverted()).isTrue();
    assertThat(inputs.secondPillarFee()).isNull();
    assertThat(inputs.canIncreasePaymentRate()).isTrue();
    assertThat(inputs.leftSecondPillar()).isEqualTo(Known.NO);
    assertThat(inputs.thirdPillarActive()).isTrue();
    assertThat(inputs.thirdPillarRecurring()).isEqualTo(Known.NO);
    assertThat(inputs.taxHeadroom()).isEqualTo(Known.UNKNOWN);
    assertThat(inputs.feeComparison()).isNull();
    assertThat(inputs.pendingSecondPillarTransfer()).isFalse();
    assertThat(inputs.pendingSecondPillarWithdrawal()).isFalse();
  }

  @Test
  void aPersonMissingFromTheRegistryHasNoPensionAccountAndCanStillBeInvitedToOpenASecondPillar() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode())).willReturn(Optional.empty());

    NudgeInputs inputs = offlineInputs.assemble(OfflineSaver.of(user), MEMBERSHIP);

    assertThat(inputs.secondPillarActive()).isFalse();
    assertThat(inputs.thirdPillarActive()).isFalse();
    assertThat(inputs.leftSecondPillar()).isEqualTo(Known.NO);
    assertThat(inputs.canIncreasePaymentRate()).isFalse();
  }

  @Test
  void aRegistryOnlyPersonIsNeverAMemberAndIsAgedFromThePersonalCode() {
    given(pensionRegistry.snapshotFor(any()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(true, false, false, true, true)));
    given(recurringStatus.thirdPillar(any())).willReturn(true);

    NudgeInputs adult =
        offlineInputs.assemble(
            OfflineSaver.registryOnly(registryPerson("38801010004")), MEMBERSHIP);
    NudgeInputs pensioner =
        offlineInputs.assemble(
            OfflineSaver.registryOnly(registryPerson("35501010000")), MEMBERSHIP);

    assertThat(adult.member()).isFalse();
    assertThat(adult.adult()).isTrue();
    assertThat(adult.reachedRetirementAge()).isFalse();
    assertThat(adult.secondPillarActive()).isTrue();
    assertThat(pensioner.reachedRetirementAge()).isTrue();
  }

  private static PersonImpl registryPerson(String personalCode) {
    return PersonImpl.builder()
        .personalCode(personalCode)
        .firstName("Registry")
        .lastName("Person")
        .build();
  }

  @Test
  void aThirdPillarContextImpliesAThirdPillarEvenBeforeTheRegistryCatchesUp() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode())).willReturn(Optional.empty());
    given(recurringStatus.thirdPillar(user.getPersonalCode())).willReturn(false);

    NudgeInputs inputs =
        offlineInputs.assemble(OfflineSaver.of(user), THIRD_PILLAR_PAYMENT_ARRIVED);

    assertThat(inputs.thirdPillarActive()).isTrue();
    assertThat(inputs.thirdPillarRecurring()).isEqualTo(Known.NO);
  }

  @Test
  void aLeaverIsMarkedFromTheRegistry() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(false, false, true, false, false)));

    NudgeInputs inputs = offlineInputs.assemble(OfflineSaver.of(user), MEMBERSHIP);

    assertThat(inputs.leftSecondPillar()).isEqualTo(Known.YES);
    assertThat(inputs.secondPillarActive()).isFalse();
  }

  @Test
  void theOfflineInputsCarryTheSameSeasonSoDecemberSuppressionReachesScheduledEmails() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(true, true, false, true, true)));

    assertThat(
            offlineInputsOn("2026-11-10")
                .assemble(OfflineSaver.of(user), MEMBERSHIP)
                .paymentRateSeason())
        .isEqualTo(
            new PaymentRateSeason(
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2027, 1, 1),
                PaymentRateSeason.Mode.SEASON));
    assertThat(
            offlineInputsOn("2026-12-10")
                .assemble(OfflineSaver.of(user), MEMBERSHIP)
                .paymentRateSeason())
        .isEqualTo(
            new PaymentRateSeason(
                LocalDate.of(2027, 11, 30),
                LocalDate.of(2028, 1, 1),
                PaymentRateSeason.Mode.CLOSED));
  }
}
