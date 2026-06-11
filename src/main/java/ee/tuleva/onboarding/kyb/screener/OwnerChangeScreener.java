package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.OWNER_CHANGED;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OwnerChangeScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var ownerChanged = companyData.ownerChangedBeforeOnboarding();
    return List.of(
        new KybCheck(
            OWNER_CHANGED, !ownerChanged, Map.of("ownerChangedBeforeOnboarding", ownerChanged)));
  }
}
