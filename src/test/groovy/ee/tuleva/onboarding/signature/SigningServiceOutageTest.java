package ee.tuleva.onboarding.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.digidoc4j.ServiceType.OCSP;
import static org.digidoc4j.ServiceType.TSP;
import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.REVOKED;
import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.TECHNICAL;
import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.UNKNOWN;
import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.UNTRUSTED;

import eu.europa.esig.dss.alert.exception.AlertException;
import java.util.stream.Stream;
import org.digidoc4j.exceptions.CertificateValidationException;
import org.digidoc4j.exceptions.ConnectionTimedOutException;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.exceptions.InvalidSignatureException;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.digidoc4j.exceptions.ServiceAccessDeniedException;
import org.digidoc4j.exceptions.ServiceUnavailableException;
import org.digidoc4j.exceptions.ServiceUnreachableException;
import org.digidoc4j.exceptions.TechnicalException;
import org.digidoc4j.exceptions.TslCertificateSourceInitializationException;
import org.digidoc4j.exceptions.TslDownloadException;
import org.digidoc4j.exceptions.TslParsingException;
import org.digidoc4j.exceptions.TslRefreshException;
import org.digidoc4j.exceptions.TslValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SigningServiceOutageTest {

  private static final String SERVICE_URL = "http://service.example.com";

  static Stream<Throwable> failuresOfTheTrustListOrTheOcspOrTimestampingService() {
    return Stream.of(
        new OCSPRequestFailedException("S-1"),
        new TslRefreshException("No TSL refresh info found!"),
        new TslDownloadException("Failed to download LoTL"),
        new TslParsingException("Failed to parse LoTL"),
        new TslValidationException("Failed to validate LoTL"),
        new TslCertificateSourceInitializationException("Failed to initialize TSL"),
        new NetworkException("Unable to process <TSP> POST call", SERVICE_URL, TSP),
        new ServiceUnreachableException(SERVICE_URL, OCSP),
        new ConnectionTimedOutException(SERVICE_URL, OCSP),
        new ServiceUnavailableException(SERVICE_URL, OCSP),
        new ServiceAccessDeniedException(SERVICE_URL, OCSP),
        CertificateValidationException.of(TECHNICAL, "OCSP service internal error"),
        CertificateValidationException.of(UNTRUSTED, "OCSP response certificate not in TSL"),
        new DigiDoc4JException(new TslDownloadException("Failed to download LoTL")),
        new TechnicalException(
            "Got error in signing process", new ServiceUnreachableException(SERVICE_URL, TSP)));
  }

  static Stream<Throwable> failuresOfTheSignerOrTheSignature() {
    return Stream.of(
        CertificateValidationException.of(REVOKED, "Certificate status is revoked"),
        CertificateValidationException.of(UNKNOWN, "Certificate is unknown"),
        new InvalidSignatureException(),
        new TechnicalException("Got error in signing process"),
        new AlertException("Timestamp integrity check failed"),
        new IllegalStateException("Unexpected"),
        new TechnicalException(
            "Got error in signing process",
            CertificateValidationException.of(REVOKED, "Certificate status is revoked")));
  }

  @ParameterizedTest
  @MethodSource("failuresOfTheTrustListOrTheOcspOrTimestampingService")
  void findsAFailureOfTheTrustListOrTheOcspOrTimestampingService(Throwable failure) {
    assertThat(SigningServiceOutage.in(failure)).isPresent();
  }

  @ParameterizedTest
  @MethodSource("failuresOfTheSignerOrTheSignature")
  void findsNoOutageInAFailureOfTheSignerOrTheSignature(Throwable failure) {
    assertThat(SigningServiceOutage.in(failure)).isEmpty();
  }

  @Test
  void namesTheFailingServiceRatherThanTheWrapperDigiDoc4jPutsAroundIt() {
    var unreachableTimestampingService = new ServiceUnreachableException(SERVICE_URL, TSP);
    var wrapped =
        new DigiDoc4JException(
            new TechnicalException("Got error in signing process", unreachableTimestampingService));

    assertThat(SigningServiceOutage.in(wrapped)).contains(unreachableTimestampingService);
  }
}
