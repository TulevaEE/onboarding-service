package ee.tuleva.onboarding.analytics;

import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.entityBuilder;
import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.unitOwnerBalanceEmbeddableBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwner;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.nudge.PensionRegistrySnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistryPensionFactsTest {

  private static final String PERSONAL_CODE = "38888888888";

  @Mock private UnitOwnerRepository unitOwnerRepository;
  @InjectMocks private RegistryPensionFacts registry;

  private static UnitOwner.UnitOwnerBuilder owner() {
    return entityBuilder(
            PERSONAL_CODE, LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 6, 0))
        .p2choice("LXK75")
        .p2ravaStatus("ACTIVE")
        .p2rate(2)
        .p2nextRate(null)
        .p3identifier(null)
        .p3identificationDate(null)
        .balances(List.of(unitOwnerBalanceEmbeddableBuilder("LXK75", "LHV").build()));
  }

  @Test
  void aSaverElsewhereWithALowRateCanBeNudgedToMoveAndToRaise() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(Optional.of(owner().build()));

    assertThat(registry.snapshotFor(PERSONAL_CODE))
        .contains(new PensionRegistrySnapshot(true, false, false, true, false));
  }

  @Test
  void aTulevaFundChoiceOrTulevaUnitsCountAsBeingAtTuleva() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(Optional.of(owner().p2choice("TUK75").build()));
    assertThat(registry.snapshotFor(PERSONAL_CODE).orElseThrow().secondPillarAtTuleva()).isTrue();

    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(
            Optional.of(
                owner()
                    .balances(List.of(unitOwnerBalanceEmbeddableBuilder("TUK00", "Tuleva").build()))
                    .build()));
    assertThat(registry.snapshotFor(PERSONAL_CODE).orElseThrow().secondPillarAtTuleva()).isTrue();
  }

  @Test
  void aLeaverIsNeitherActiveNorNudgeable() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(Optional.of(owner().p2ravaStatus("R").build()));

    assertThat(registry.snapshotFor(PERSONAL_CODE))
        .contains(new PensionRegistrySnapshot(false, false, true, true, false));
  }

  @Test
  void theMaximumRateCannotBeRaisedAndAPendingMaximumCountsAsReached() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(Optional.of(owner().p2rate(2).p2nextRate(6).build()));

    assertThat(registry.snapshotFor(PERSONAL_CODE).orElseThrow().canIncreasePaymentRate())
        .isFalse();
  }

  @Test
  void aThirdPillarShowsThroughTheIdentifierOrTulevaThirdPillarUnits() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(Optional.of(owner().p3identifier("ID_CARD").build()));
    assertThat(registry.snapshotFor(PERSONAL_CODE).orElseThrow().thirdPillarActive()).isTrue();

    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE))
        .willReturn(
            Optional.of(
                owner()
                    .balances(
                        List.of(unitOwnerBalanceEmbeddableBuilder("TUV100", "Tuleva").build()))
                    .build()));
    assertThat(registry.snapshotFor(PERSONAL_CODE).orElseThrow().thirdPillarActive()).isTrue();
  }

  @Test
  void nobodyInTheRegistryMeansNoSnapshot() {
    given(unitOwnerRepository.findInLatestSnapshot(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThat(registry.snapshotFor(PERSONAL_CODE)).isEmpty();
  }
}
