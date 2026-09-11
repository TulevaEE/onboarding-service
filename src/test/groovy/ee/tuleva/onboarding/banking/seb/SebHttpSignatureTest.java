package ee.tuleva.onboarding.banking.seb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

class SebHttpSignatureTest {

  private static final String KEYSTORE_PATH = "src/test/resources/banking/seb/test-seb-gateway.p12";
  private static final String KEYSTORE_PASSWORD = "testpass";
  private static final String KEY_ALIAS = "test";

  @Test
  void createDigest_hashesBase64EncodedBody() throws Exception {
    var signature = new SebHttpSignature(loadTestPrivateKey(), "unused-key-id");

    String digest = signature.createDigest("test-signing-body".getBytes(UTF_8));

    assertThat(digest).isEqualTo("SHA-256=VjV+H4T0smAd6uGlpbe7mttq1wLWUbmvdhI9ueSiGv0=");
  }

  @Test
  void createSignature_producesPinnedRsaSignatureHeader() throws Exception {
    var signature =
        new SebHttpSignature(loadTestPrivateKey(), "SN=e079cedfa8cf463f,CA=CN=Test,O=Test");

    String header =
        signature.createSignature("SHA-256=VjV+H4T0smAd6uGlpbe7mttq1wLWUbmvdhI9ueSiGv0=");

    assertThat(header)
        .isEqualTo(
            "keyId=\"SN=e079cedfa8cf463f,CA=CN=Test,O=Test\",algorithm=\"rsa-sha256\",headers=\"digest\",signature=\"R3ihIxYJjBmVUhLNqF/42VcE5vqFupfxZE/6JMpHFyJaYLAfh4ljCi5kOKY2PPQC646lKB9JU9bX5+ewHXezqu80YGKOBpy8Q44Lsd9s7NXbg83u5DOfTgLzIvCuY+iPDnV5IE/v4s4ijnKP43UstKmkBuVavq1HFjHNMXd3nZ9joKXAocZONqOF3DTKauqn9guAlvRiqdGnMXVXzELoUlUYsOd43HQPj3bTTwp4T5gr/pyCsQg68qKv2sIn6HWu0k46qpsunEVaCQD3kM2/AJ5lGfoXuz6FV0w6/6y4qVQvdOU1L/Ln9XBB50/omIwBzL8klpPTLVe9UjmikV5bCw==\"");
  }

  @Test
  void buildKeyId_padsOddLengthSerialAndOrdersCnBeforeO() throws Exception {
    X509Certificate certificate = SebTestCertificates.selfSigned("CN=Alpha,O=Beta", BigInteger.ONE);

    String keyId = SebHttpSignature.buildKeyId(certificate);

    assertThat(keyId).isEqualTo("SN=01,CA=CN=Alpha,O=Beta");
  }

  @Test
  void buildKeyId_evenLengthSerialIsNotPadded() throws Exception {
    X509Certificate certificate = loadTestCertificate();

    String keyId = SebHttpSignature.buildKeyId(certificate);

    assertThat(keyId).isEqualTo("SN=e079cedfa8cf463f,CA=CN=Test,O=Test");
  }

  @Test
  void buildKeyId_fallsBackToRawDnWhenOrganizationMissing() throws Exception {
    X509Certificate certificate = SebTestCertificates.selfSigned("CN=OnlyCommon", BigInteger.TEN);

    String keyId = SebHttpSignature.buildKeyId(certificate);

    assertThat(keyId).isEqualTo("SN=0a,CA=CN=OnlyCommon");
  }

  @Test
  void buildKeyId_fallsBackToRawDnWhenCommonNameMissing() throws Exception {
    X509Certificate certificate = SebTestCertificates.selfSigned("O=OnlyOrg", BigInteger.TWO);

    String keyId = SebHttpSignature.buildKeyId(certificate);

    assertThat(keyId).isEqualTo("SN=02,CA=O=OnlyOrg");
  }

  private static PrivateKey loadTestPrivateKey() throws Exception {
    return (PrivateKey) loadTestKeyStore().getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray());
  }

  private static X509Certificate loadTestCertificate() throws Exception {
    return (X509Certificate) loadTestKeyStore().getCertificate(KEY_ALIAS);
  }

  private static KeyStore loadTestKeyStore() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = new FileInputStream(KEYSTORE_PATH)) {
      keyStore.load(in, KEYSTORE_PASSWORD.toCharArray());
    }
    return keyStore;
  }
}
