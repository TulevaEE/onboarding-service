package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.nudge.NudgeContext.MEMBERSHIP;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT_ARRIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
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

  private final User user = sampleUser().build();
  private OfflineNudgeInputs offlineInputs;

  @BeforeEach
  void setUp() {
    offlineInputs =
        new OfflineNudgeInputs(
            pensionRegistry,
            new KnownLookups(
                leaverStatus,
                recurringStatus,
                saverStatus,
                taxHeadroom,
                actingParties,
                savingsFundFeeRate));
    given(savingsFundFeeRate.ongoingChargesPercent()).willReturn(new BigDecimal("0.28"));
    given(recurringStatus.savingsFund(any())).willReturn(true);
    given(saverStatus.savesFor(any())).willReturn(true);
  }

  @Test
  void readsThePensionFactsFromTheRegistrySnapshotAndLeavesEpisOnlyFactsUnknown() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(true, true, false, true, true)));
    given(recurringStatus.thirdPillar(user.getPersonalCode())).willReturn(false);

    NudgeInputs inputs = offlineInputs.assemble(user, MEMBERSHIP);

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
  void aPersonMissingFromTheRegistryHasNoSecondPillarAndNoThirdPillar() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode())).willReturn(Optional.empty());

    NudgeInputs inputs = offlineInputs.assemble(user, MEMBERSHIP);

    assertThat(inputs.secondPillarActive()).isFalse();
    assertThat(inputs.thirdPillarActive()).isFalse();
    assertThat(inputs.leftSecondPillar()).isEqualTo(Known.NO);
    assertThat(inputs.canIncreasePaymentRate()).isFalse();
  }

  @Test
  void aThirdPillarContextImpliesAThirdPillarEvenBeforeTheRegistryCatchesUp() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode())).willReturn(Optional.empty());
    given(recurringStatus.thirdPillar(user.getPersonalCode())).willReturn(false);

    NudgeInputs inputs = offlineInputs.assemble(user, THIRD_PILLAR_PAYMENT_ARRIVED);

    assertThat(inputs.thirdPillarActive()).isTrue();
    assertThat(inputs.thirdPillarRecurring()).isEqualTo(Known.NO);
  }

  @Test
  void aLeaverIsMarkedFromTheRegistry() {
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(false, false, true, false, false)));

    NudgeInputs inputs = offlineInputs.assemble(user, MEMBERSHIP);

    assertThat(inputs.leftSecondPillar()).isEqualTo(Known.YES);
    assertThat(inputs.secondPillarActive()).isFalse();
  }
}
