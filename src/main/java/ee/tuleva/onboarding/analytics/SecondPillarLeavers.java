package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.nudge.SecondPillarLeaverStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecondPillarLeavers implements SecondPillarLeaverStatus {

  private final UnitOwnerRepository unitOwnerRepository;

  @Override
  public boolean hasLeft(String personalCode) {
    return unitOwnerRepository.hasLeftSecondPillar(personalCode);
  }
}
