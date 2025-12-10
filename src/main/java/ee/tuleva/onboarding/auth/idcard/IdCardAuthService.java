package ee.tuleva.onboarding.auth.idcard;

import ee.tuleva.onboarding.auth.ocsp.CheckCertificateResponse;
import ee.tuleva.onboarding.auth.ocsp.OCSPAuthService;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.security.cert.X509Certificate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class IdCardAuthService {

  private final OCSPAuthService ocspAuthenticator;
  private final GenericSessionStore sessionStore;
  private final OCSPUtils ocspUtils;
  private final IdDocumentTypeExtractor documentTypeExtractor;

  public IdCardSession checkCertificate(String certificate) {
    log.info("Checking ID card certificate");
    X509Certificate x509Certificate = ocspUtils.getX509Certificate(certificate);
    CheckCertificateResponse response = ocspAuthenticator.checkCertificate(x509Certificate);

    IdDocumentType documentType = documentTypeExtractor.extract(x509Certificate);
    documentTypeExtractor.checkClientAuthentication(x509Certificate);
    documentTypeExtractor.checkIssuer(x509Certificate);

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
}
