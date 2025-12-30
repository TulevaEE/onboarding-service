package ee.tuleva.onboarding.auth.idcard.normalizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestCertificateNormalizerTest {

  private final TestCertificateNormalizer normalizer = new TestCertificateNormalizer();

  @Test
  void normalizeOid_stripsTestPrefix() {
    assertThat(normalizer.normalizeOid("2.999.1.3.6.1.4.1.51361.2.1.1"))
        .isEqualTo("1.3.6.1.4.1.51361.2.1.1");
  }

  @Test
  void normalizeOid_returnsUnchangedForProductionOid() {
    assertThat(normalizer.normalizeOid("1.3.6.1.4.1.51361.2.1.1"))
        .isEqualTo("1.3.6.1.4.1.51361.2.1.1");
  }

  @Test
  void normalizeIssuer_stripsTestOfPrefix() {
    assertThat(
            normalizer.normalizeIssuer(
                "CN=TEST of ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE"))
        .isEqualTo(
            "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE");
  }

  @Test
  void normalizeIssuer_stripsTestPrefix() {
    assertThat(
            normalizer.normalizeIssuer(
                "C=EE, O=Zetes Estonia OÜ, OID.2.5.4.97=NTREE-17066049, CN=Test ESTEID2025"))
        .isEqualTo("C=EE, O=Zetes Estonia OÜ, OID.2.5.4.97=NTREE-17066049, CN=ESTEID2025");
  }

  @Test
  void normalizeIssuer_returnsUnchangedForProductionIssuer() {
    assertThat(
            normalizer.normalizeIssuer(
                "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE"))
        .isEqualTo(
            "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE");
  }
}
