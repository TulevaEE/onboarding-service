package ee.tuleva.onboarding.auth.idcard;

import static org.bouncycastle.asn1.x509.Extension.certificatePolicies;
import static org.bouncycastle.asn1.x509.Extension.extendedKeyUsage;
import static org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils.parseExtensionValue;

import ee.tuleva.onboarding.auth.idcard.exception.UnknownDocumentTypeException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownExtendedKeyUsageException;
import ee.tuleva.onboarding.auth.idcard.exception.UnknownIssuerException;
import ee.tuleva.onboarding.auth.ocsp.CheckCertificateResponse;
import ee.tuleva.onboarding.auth.ocsp.OCSPAuthService;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import javax.security.auth.x500.X500Principal;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DLSequence;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class IdCardAuthService {

  private static final String AUTHENTICATION_POLICY_ID = "0.4.0.2042.1.2";
  private static final String CLIENT_AUTHENTICATION_ID = "1.3.6.1.5.5.7.3.2";
  private static final int POLICY_NO_1 = 0;
  private static final int POLICY_NO_2 = 1;
  private static final List<String> VALID_ISSUERS =
      List.of(
          "CN=ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE",
          "CN=ESTEID2018, OID.2.5.4.97=NTREE-10747013, O=SK ID Solutions AS, C=EE",
          "CN=ESTEID2025, OID.2.5.4.97=NTREE-17066049, O=Zetes Estonia OÜ, C=EE");

  private final OCSPAuthService ocspAuthenticator;
  private final GenericSessionStore sessionStore;
  private final OCSPUtils ocspUtils;

  public IdCardSession checkCertificate(String certificate) {
    log.info("Checking ID card certificate");
    X509Certificate x509Certificate = ocspUtils.getX509Certificate(certificate);
    CheckCertificateResponse response = ocspAuthenticator.checkCertificate(x509Certificate);

    IdDocumentType documentType = getDocumentTypeFromCertificate(x509Certificate);
    checkClientAuthentication(x509Certificate);
    checkIssuer(x509Certificate);

    IdCardSession session =
        IdCardSession.builder()
            .firstName(response.getFirstName())
            .lastName(response.getLastName())
            .personalCode(response.getPersonalCode())
            .documentType(documentType)
            .build();
    sessionStore.save(session);
    return session;
  }

  public IdDocumentType getDocumentTypeFromCertificate(X509Certificate certificate) {
    try {
      byte[] encodedExtensionValue = certificate.getExtensionValue(certificatePolicies.getId());
      if (encodedExtensionValue != null) {
        final var extensionValue = (DLSequence) parseExtensionValue(encodedExtensionValue);
        try {
          final var first = (DLSequence) extensionValue.getObjectAt(POLICY_NO_1);
          final var second = (DLSequence) extensionValue.getObjectAt(POLICY_NO_2);
          if (Objects.equals(second.getObjectAt(0).toString(), AUTHENTICATION_POLICY_ID)) {
            return IdDocumentType.findByIdentifier(first.getObjectAt(POLICY_NO_1).toString());
          } else {
            throw new UnknownDocumentTypeException(second.getObjectAt(0).toString());
          }
        } catch (ArrayIndexOutOfBoundsException e) {
          log.error("Extension of card certificate not known: {}", extensionValue.toString());
        }
      } else {
        log.error("Extension missing!");
      }
    } catch (IOException e) {
      log.error("Could not parse certificate", e);
    }
    throw new UnknownDocumentTypeException();
  }

  public void checkClientAuthentication(X509Certificate certificate) {
    try {
      byte[] encodedExtendedKeyUsage = certificate.getExtensionValue(extendedKeyUsage.getId());
      if (encodedExtendedKeyUsage != null) {
        final var extendedKeyUsage = (DLSequence) parseExtensionValue(encodedExtendedKeyUsage);
        for (final var element : extendedKeyUsage) {
          if (element.toString().equals(CLIENT_AUTHENTICATION_ID)) {
            return;
          }
        }
        throw new UnknownExtendedKeyUsageException(extendedKeyUsage.toString());
      } else {
        log.error("Extension missing!");
      }
    } catch (IOException e) {
      log.error("Could not parse certificate", e);
    }
    throw new UnknownExtendedKeyUsageException();
  }

  private void checkIssuer(X509Certificate certificate) {
    final var issuer = certificate.getIssuerX500Principal().getName(X500Principal.RFC1779);
    if (VALID_ISSUERS.contains(issuer)) {
      return;
    }
    throw new UnknownIssuerException(issuer);
  }
}
