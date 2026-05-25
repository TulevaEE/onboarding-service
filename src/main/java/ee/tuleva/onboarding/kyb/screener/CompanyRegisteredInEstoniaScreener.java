package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_REGISTERED_IN_ESTONIA;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class CompanyRegisteredInEstoniaScreener implements KybScreener {

  private static final String ESTONIA = "EE";

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var countryCode = companyData.countryCode();
    var registeredInEstonia =
        ESTONIA.equalsIgnoreCase(countryCode)
            || (StringUtils.isBlank(countryCode)
                && EstonianCounty.isPresentIn(companyData.fullAddress()));
    return List.of(
        new KybCheck(
            COMPANY_REGISTERED_IN_ESTONIA,
            registeredInEstonia,
            Map.of("countryCode", StringUtils.defaultString(countryCode))));
  }
}
