package ee.tuleva.onboarding.auth.ocsp;

import static ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code.UNABLE_TO_TEST_USER_CERTIFICATE;
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.EXPIRED;
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.GOOD;
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.REVOKED;
import static ee.tuleva.onboarding.auth.ocsp.OCSPResponseType.UNKNOWN;
import static java.time.Duration.ofSeconds;
import static org.bouncycastle.cert.ocsp.OCSPResp.MALFORMED_REQUEST;
import static org.bouncycastle.cert.ocsp.OCSPResp.SUCCESSFUL;
import static org.bouncycastle.cert.ocsp.OCSPResp.UNAUTHORIZED;
import static org.springframework.http.HttpMethod.POST;

import ee.tuleva.onboarding.auth.ocsp.AuthenticationException.Code;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@Slf4j
@Service
public class OCSPService {
  public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
  public static final String END_CERT = "-----END CERTIFICATE-----";
  public static final String LINE_SEPARATOR = System.lineSeparator();
  private final RestOperations restTemplate;
  private final Clock clock;

  public OCSPService(RestTemplateBuilder restTemplateBuilder, Clock clock) {
    restTemplate =
        restTemplateBuilder.connectTimeout(ofSeconds(60)).readTimeout(ofSeconds(60)).build();
    this.clock = clock;
  }

  public OCSPResponseType checkCertificate(OCSPRequest request) {
    X509Certificate certificate = request.getCertificate();
    if (hasCertificateExpired(certificate)) {
      return EXPIRED;
    }
    return checkCertificateStatus(request);
  }

  private boolean hasCertificateExpired(X509Certificate certificate) {
    Instant currentTime = clock.instant();
    return currentTime.isAfter(certificate.getNotAfter().toInstant());
  }

  public String getIssuerCertificate(String url) {

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));
    HttpEntity<String> entity = new HttpEntity<>(headers);

    ResponseEntity<byte[]> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

    byte[] derCert = response.getBody();
    final Base64.Encoder encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.getBytes());
    final String encodedCertText = new String(encoder.encode(derCert));
    final String pemCert =
        BEGIN_CERT + LINE_SEPARATOR + encodedCertText + LINE_SEPARATOR + END_CERT;

    return pemCert;
  }

  private OCSPResponseType checkCertificateStatus(OCSPRequest request) {
    try {
      log.info("Generating and sending OCSPRequest");
      OCSPResp response = getOCSPResponse(request);
      return validateOCSPResponse(response);
    } catch (OCSPException | RestClientException | IOException e) {
      throw new AuthenticationException(
          UNABLE_TO_TEST_USER_CERTIFICATE, "Couldn't validate serial number", e);
    }
  }

  private OCSPResponseType validateOCSPResponse(OCSPResp response) throws OCSPException {
    OCSPResponseType status = UNKNOWN;
    switch (response.getStatus()) {
      case SUCCESSFUL:
        BasicOCSPResp ocspResponseData = (BasicOCSPResp) response.getResponseObject();
        SingleResp[] responses = ocspResponseData.getResponses();
        for (SingleResp ocspResponse : responses) {
          if (ocspResponse.getCertStatus() == null) {
            status = GOOD;
          } else if (ocspResponse.getCertStatus() instanceof RevokedStatus) {
            status = REVOKED;
          }
          log.info("OCSP response code: {}", status);
        }
        break;
      case MALFORMED_REQUEST:
        throw new AuthenticationException(UNABLE_TO_TEST_USER_CERTIFICATE, "Malformed request");
      case UNAUTHORIZED:
        throw new AuthenticationException(Code.UNAUTHORIZED, "Unauthorized access");
      default:
        throw new AuthenticationException(UNABLE_TO_TEST_USER_CERTIFICATE, "Uncaught error");
    }
    return status;
  }

  private OCSPResp getOCSPResponse(OCSPRequest request) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/ocsp-request");
    headers.set("Accept", "application/ocsp-response");
    HttpEntity<byte[]> entity = new HttpEntity<>(request.getOcspRequest().getEncoded(), headers);
    ResponseEntity<byte[]> result =
        restTemplate.exchange(request.getOcspServer(), POST, entity, byte[].class);
    return new OCSPResp(result.getBody());
  }
}
