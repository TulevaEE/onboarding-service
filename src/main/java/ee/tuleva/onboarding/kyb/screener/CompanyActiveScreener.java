package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CompanyActiveScreener implements KybScreener {

  @Override
  public Optional<KybCheck> screen(KybCompanyData companyData) {
    return Optional.of(
        new KybCheck(
            COMPANY_ACTIVE,
            companyData.status().isActive(),
            Map.of("status", companyData.status().name())));
  }
}
