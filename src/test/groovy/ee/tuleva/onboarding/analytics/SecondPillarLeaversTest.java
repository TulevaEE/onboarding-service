package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecondPillarLeaversTest {

  @Mock private UnitOwnerRepository unitOwnerRepository;
  @InjectMocks private SecondPillarLeavers secondPillarLeavers;

  @Test
  void hasLeftDelegatesToTheRepositoryAndReturnsTrue() {
    given(unitOwnerRepository.hasLeftSecondPillar("38888888888")).willReturn(true);

    assertThat(secondPillarLeavers.hasLeft("38888888888")).isTrue();
  }

  @Test
  void hasLeftDelegatesToTheRepositoryAndReturnsFalse() {
    given(unitOwnerRepository.hasLeftSecondPillar("38888888888")).willReturn(false);

    assertThat(secondPillarLeavers.hasLeft("38888888888")).isFalse();
  }
}
