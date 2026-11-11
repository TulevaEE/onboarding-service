package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PARTIES;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CompanyPartiesScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    return List.of(
        new KybCheck(
            COMPANY_PARTIES, true, CompanyPartiesMetadata.of(companyData.relatedPersons())));
  }
}
