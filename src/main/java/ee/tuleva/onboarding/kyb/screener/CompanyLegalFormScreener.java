package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_LEGAL_FORM;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompanyLegalFormScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var legalForm = companyData.company().legalForm();
    var accepted = legalForm != null && legalForm.isAccepted();
    return List.of(
        new KybCheck(COMPANY_LEGAL_FORM, accepted, Map.of("legalForm", String.valueOf(legalForm))));
  }
}
