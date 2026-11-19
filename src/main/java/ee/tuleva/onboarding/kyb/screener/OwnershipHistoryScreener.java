package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.OWNERSHIP_HISTORY;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OwnershipHistoryScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    return List.of(
        new KybCheck(
            OWNERSHIP_HISTORY,
            true,
            OwnershipHistoryMetadata.of(companyData.registerHistory().ownerships())));
  }
}
