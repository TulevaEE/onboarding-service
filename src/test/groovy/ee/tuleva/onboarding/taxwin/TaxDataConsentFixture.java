package ee.tuleva.onboarding.taxwin;

import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

final class TaxDataConsentFixture {

  static final Instant CREATED_AT = Instant.parse("2026-11-18T10:00:00Z");
  static final String CONFIRM_REFERENCE = "5b1c4f2e-7d3a-4a1e-9c2f-0d8e6b7a1c3d";
  static final String CONSENT_REFERENCE = "consent-reference-1";
  static final byte[] CONSENT_PDF = {1, 2, 3};
  static final byte[] SIGNED_CONTAINER = {4, 5, 6};

  private TaxDataConsentFixture() {}

  static TaxDataConsent awaitingSignature(Long id, Long userId) {
    var consent = new TaxDataConsent(userId, CREATED_AT);
    ReflectionTestUtils.setField(consent, "id", id);
    consent.awaitSignatureOf(new ConsentDocument(CONFIRM_REFERENCE, CONSENT_PDF), CREATED_AT);
    return consent;
  }

  static TaxDataConsent signed(Long id, Long userId) {
    var consent = awaitingSignature(id, userId);
    consent.sign(SIGNED_CONTAINER);
    return consent;
  }

  static TaxDataConsent approved(Long id, Long userId) {
    var consent = awaitingSignature(id, userId);
    consent.approve(CONSENT_REFERENCE);
    return consent;
  }

  static TaxDataConsent refreshed(Long id, Long userId, Instant refreshedAt) {
    var consent = approved(id, userId);
    consent.refreshed(refreshedAt);
    return consent;
  }
}
