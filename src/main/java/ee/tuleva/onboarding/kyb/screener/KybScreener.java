package ee.tuleva.onboarding.kyb.screener;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;

public interface KybScreener {

  List<KybCheck> screen(KybCompanyData companyData);
}
