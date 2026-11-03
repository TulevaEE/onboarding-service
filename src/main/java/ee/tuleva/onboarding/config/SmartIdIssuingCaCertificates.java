package ee.tuleva.onboarding.config;

import ee.sk.smartid.TrustedCACertStore;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;

final class SmartIdIssuingCaCertificates {

  private SmartIdIssuingCaCertificates() {}

  static List<X509Certificate> of(TrustedCACertStore store) {
    return Stream.concat(
            store.getTrustAnchors().stream().map(TrustAnchor::getTrustedCert),
            store.getTrustedCACertificates().stream())
        .toList();
  }
}
