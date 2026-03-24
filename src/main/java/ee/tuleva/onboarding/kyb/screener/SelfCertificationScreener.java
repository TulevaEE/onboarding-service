package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SELF_CERTIFICATION;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.SelfCertification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SelfCertificationScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var cert = companyData.selfCertification();
    boolean success =
        cert != null
            && cert.operatesInEstonia()
            && cert.notSanctioned()
            && cert.noHighRiskActivity();

    return List.of(new KybCheck(SELF_CERTIFICATION, success, buildMetadata(cert)));
  }

  private Map<String, Object> buildMetadata(SelfCertification cert) {
    if (cert == null) {
      return Map.of();
    }
    return Map.of(
        "operatesInEstonia", cert.operatesInEstonia(),
        "notSanctioned", cert.notSanctioned(),
        "noHighRiskActivity", cert.noHighRiskActivity());
  }
}
