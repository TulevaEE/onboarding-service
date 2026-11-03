package ee.tuleva.onboarding.signature;

import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.TECHNICAL;
import static org.digidoc4j.exceptions.CertificateValidationException.CertificateValidationStatus.UNTRUSTED;

import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.digidoc4j.CertificateStatus;
import org.digidoc4j.exceptions.CertificateValidationException;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.digidoc4j.exceptions.TslCertificateSourceInitializationException;
import org.digidoc4j.exceptions.TslRefreshException;

final class SigningServiceOutage {

  private SigningServiceOutage() {}

  static Optional<Throwable> in(Throwable failure) {
    return ExceptionUtils.getThrowableList(failure).stream()
        .filter(SigningServiceOutage::isFailureOfTheTrustListOrTheOcspOrTimestampingService)
        .findFirst();
  }

  private static boolean isFailureOfTheTrustListOrTheOcspOrTimestampingService(Throwable failure) {
    final Set<CertificateStatus> FAILURES_OF_THE_OCSP_SERVICE_RATHER_THAN_THE_CERTIFICATE =
        Set.of(TECHNICAL, UNTRUSTED);
    return switch (failure) {
      case OCSPRequestFailedException _,
          TslRefreshException _,
          TslCertificateSourceInitializationException _,
          NetworkException _ ->
          true;
      case CertificateValidationException validationFailure ->
          FAILURES_OF_THE_OCSP_SERVICE_RATHER_THAN_THE_CERTIFICATE.contains(
              validationFailure.getCertificateStatus());
      default -> false;
    };
  }
}
