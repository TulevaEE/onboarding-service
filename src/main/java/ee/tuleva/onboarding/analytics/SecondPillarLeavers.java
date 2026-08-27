package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecondPillarLeavers {

  private final UnitOwnerRepository unitOwnerRepository;

  public boolean hasLeft(String personalCode) {
    return unitOwnerRepository.hasLeftSecondPillar(personalCode);
  }
}
