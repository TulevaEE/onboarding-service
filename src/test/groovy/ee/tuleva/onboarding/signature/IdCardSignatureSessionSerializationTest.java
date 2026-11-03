package ee.tuleva.onboarding.signature;

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.HTML;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import ee.tuleva.onboarding.config.DigiDocConfiguration;
import ee.tuleva.onboarding.time.ClockConfig;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.junit.jupiter.api.Test;

class IdCardSignatureSessionSerializationTest {

  private final SignableEntity mandate = new SignableEntity("Mandate", 1L);

  @Test
  void survivesTheJdbcSessionStoreWithTheProductionDigiDocConfigurationAndTheApplicationClock() {
    DigiDocFacade facade =
        new DigiDocFacade(new DigiDocConfiguration().digiDocConfigProd(new ClockConfig().clock()));
    Container container =
        facade.buildContainer(List.of(new SignatureFile("file.txt", HTML, "content".getBytes())));
    DataToSign dataToSign =
        facade.dataToSign(
            container,
            WebEidCertificateFixture.certificate(
                "TEST", "USER", "38888888888", ESTONIAN_CITIZEN_ID_CARD),
            DigestAlgorithm.SHA256);
    IdCardSignatureSession session =
        new IdCardSignatureSession(mandate, "hash", "SHA-256", dataToSign, container);

    IdCardSignatureSession restored = SerializationUtils.roundtrip(session);

    assertThat(restored.getSignableEntity()).isEqualTo(mandate);
    assertThat(restored.getHashToSign()).isEqualTo("hash");
    assertThat(restored.getDataToSign().getDataToSign()).isEqualTo(dataToSign.getDataToSign());
  }
}
