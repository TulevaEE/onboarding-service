package ee.tuleva.onboarding.config;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

import ee.sk.smartid.DefaultTrustedCAStoreBuilder;
import ee.sk.smartid.TrustedCACertStore;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;

final class SmartIdTrustedCaCertificates {

  private SmartIdTrustedCaCertificates() {}

  static TrustedCACertStore load(ResourceLoader resourceLoader, String locationPattern) {
    List<X509Certificate> certificates = read(resourceLoader, locationPattern);
    if (certificates.isEmpty()) {
      throw new SmartIdClientException(
          "No Smart-ID trusted CA certificates found: locationPattern=" + locationPattern);
    }
    Set<TrustAnchor> trustAnchors =
        certificates.stream()
            .filter(SmartIdTrustedCaCertificates::isSelfSigned)
            .map(certificate -> new TrustAnchor(certificate, null))
            .collect(toSet());
    List<X509Certificate> intermediates =
        certificates.stream().filter(not(SmartIdTrustedCaCertificates::isSelfSigned)).toList();
    return new DefaultTrustedCAStoreBuilder()
        .withTrustAnchors(trustAnchors)
        .withIntermediateCACertificate(intermediates)
        .withOcspEnabled(false)
        .build();
  }

  private static List<X509Certificate> read(ResourceLoader resourceLoader, String locationPattern) {
    try {
      Resource[] resources =
          ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
              .getResources(locationPattern);
      return Arrays.stream(resources).map(SmartIdTrustedCaCertificates::read).toList();
    } catch (IOException e) {
      throw new SmartIdClientException(
          "Error reading Smart-ID trusted CA certificates: locationPattern=" + locationPattern, e);
    }
  }

  private static X509Certificate read(Resource resource) {
    try (InputStream inputStream = resource.getInputStream()) {
      return (X509Certificate)
          CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
    } catch (IOException | GeneralSecurityException e) {
      throw new SmartIdClientException(
          "Error reading Smart-ID trusted CA certificate: resource=" + resource, e);
    }
  }

  private static boolean isSelfSigned(X509Certificate certificate) {
    if (!certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
      return false;
    }
    try {
      certificate.verify(certificate.getPublicKey());
      return true;
    } catch (GeneralSecurityException e) {
      return false;
    }
  }
}
