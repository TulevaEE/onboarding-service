package ee.tuleva.onboarding.auth.idcard.normalizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionCertificateNormalizerTest {

  private final ProductionCertificateNormalizer normalizer = new ProductionCertificateNormalizer();

  @Test
  void normalizeOid_returnsUnchanged() {
    assertThat(normalizer.normalizeOid("1.3.6.1.4.1.51361.2.1.1"))
        .isEqualTo("1.3.6.1.4.1.51361.2.1.1");
  }

  @Test
  void normalizeOid_doesNotStripTestPrefix() {
    assertThat(normalizer.normalizeOid("2.999.1.3.6.1.4.1.51361.2.1.1"))
        .isEqualTo("2.999.1.3.6.1.4.1.51361.2.1.1");
  }

  @Test
  void normalizeIssuer_returnsUnchanged() {
    assertThat(
            normalizer.normalizeIssuer(
                "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE"))
        .isEqualTo(
            "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE");
  }

  @Test
  void normalizeIssuer_doesNotStripTestPrefix() {
    assertThat(
            normalizer.normalizeIssuer(
                "CN=TEST of ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE"))
        .isEqualTo(
            "CN=TEST of ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE");
  }
}
