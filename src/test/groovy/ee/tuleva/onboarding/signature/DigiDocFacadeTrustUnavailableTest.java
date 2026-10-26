package ee.tuleva.onboarding.signature;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.digidoc4j.Configuration.Mode.TEST;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.List;
import org.digidoc4j.Configuration;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.digidoc4j.exceptions.TslDownloadException;
import org.digidoc4j.impl.asic.tsl.TSLCertificateSourceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DigiDocFacadeTrustUnavailableTest {

  private static final String UNREACHABLE = "http://localhost:1/";

  private final LocalTimestampAuthority timestampAuthority = new LocalTimestampAuthority();

  @AfterEach
  void stopTimestampAuthority() {
    timestampAuthority.close();
  }

  @Test
  void reportsSigningAsUnavailableWhenTheTrustListCannotBeDownloaded() {
    var configuration = offlineConfiguration(timestampAuthority.url());
    configuration.setLotlLocation(UNREACHABLE + "lotl.xml");

    assertThatThrownBy(() -> finalizeSignature(configuration))
        .isInstanceOf(SigningServiceUnavailableException.class)
        .hasCauseInstanceOf(TslDownloadException.class);
  }

  @Test
  void reportsSigningAsUnavailableWhenNoTrustedListIsLoadedSoTheSignatureGetsNoOcspResponse() {
    var configuration = offlineConfiguration(timestampAuthority.url());
    configuration.setTSL(new TSLCertificateSourceImpl());

    assertThatThrownBy(() -> finalizeSignature(configuration))
        .isInstanceOf(SigningServiceUnavailableException.class)
        .hasCauseInstanceOf(OCSPRequestFailedException.class);
  }

  @Test
  void reportsSigningAsUnavailableWhenTheTimestampingServiceIsUnreachable() {
    var configuration = offlineConfiguration(UNREACHABLE + "tsa");
    configuration.setTSL(new TSLCertificateSourceImpl());

    assertThatThrownBy(() -> finalizeSignature(configuration))
        .isInstanceOf(SigningServiceUnavailableException.class)
        .hasCauseInstanceOf(NetworkException.class);
  }

  private static Configuration offlineConfiguration(String timestampingServiceUrl) {
    var configuration = new Configuration(TEST);
    configuration.setTspSource(timestampingServiceUrl);
    configuration.setOcspSource(UNREACHABLE + "ocsp");
    return configuration;
  }

  private static void finalizeSignature(Configuration configuration)
      throws GeneralSecurityException {
    var facade = new DigiDocFacade(configuration);
    var signer = WebEidCertificateFixture.signingCertificate("38888888888", "RSA", 2048);
    var container =
        facade.buildContainer(
            List.of(new SignatureFile("file.txt", "text/plain", "content".getBytes())));
    var dataToSign = facade.dataToSign(container, signer.certificate(), DigestAlgorithm.SHA256);
    var signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(signer.privateKey());
    signature.update(dataToSign.getDataToSign());

    facade.addSignatureToContainer(signature.sign(), dataToSign, container);
  }
}
