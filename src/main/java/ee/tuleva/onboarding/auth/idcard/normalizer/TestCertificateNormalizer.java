package ee.tuleva.onboarding.auth.idcard.normalizer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("staging")
public class TestCertificateNormalizer implements CertificateNormalizer {

  private static final String TEST_OID_PREFIX = "2.999.";
  private static final String TEST_ISSUER_PREFIX_1 = "TEST of ";
  private static final String TEST_ISSUER_PREFIX_2 = "Test ";

  @Override
  public String normalizeOid(String oid) {
    if (oid.startsWith(TEST_OID_PREFIX)) {
      return oid.substring(TEST_OID_PREFIX.length());
    }
    return oid;
  }

  @Override
  public String normalizeIssuer(String issuer) {
    return issuer.replace(TEST_ISSUER_PREFIX_1, "").replace(TEST_ISSUER_PREFIX_2, "");
  }
}
