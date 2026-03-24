package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompanyActiveScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    return List.of(
        new KybCheck(
            COMPANY_ACTIVE,
            companyData.status().isActive(),
            Map.of("status", companyData.status().name())));
  }
}
