package ee.tuleva.onboarding.kyb.screener;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.Optional;

public interface KybScreener {

  Optional<KybCheck> screen(KybCompanyData companyData);
}
