package ee.tuleva.onboarding.auth.idcard.normalizer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!staging")
public class ProductionCertificateNormalizer implements CertificateNormalizer {

  @Override
  public String normalizeOid(String oid) {
    return oid;
  }

  @Override
  public String normalizeIssuer(String issuer) {
    return issuer;
  }
}
