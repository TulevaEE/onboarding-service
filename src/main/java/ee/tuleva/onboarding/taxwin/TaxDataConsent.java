package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.SIGNED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tax_data_consent")
@NoArgsConstructor(access = PROTECTED)
class TaxDataConsent {

  static final Duration CONSENT_SERVICE_SIGNATURE_WINDOW = Duration.ofDays(1);

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private Long userId;

  @Enumerated(STRING)
  private TaxDataConsentStatus status;

  private @Nullable String consentConfirmReference;

  private byte @Nullable [] consentDocument;

  private byte @Nullable [] signedContainer;

  private @Nullable String consentReference;

  private @Nullable Instant refreshedAt;

  private @Nullable Instant leaseUntil;

  private Instant createdAt;

  TaxDataConsent(Long userId, Instant createdAt) {
    this.userId = userId;
    this.status = INVALID;
    this.createdAt = createdAt;
  }

  TaxDataConsentStatus getStatus() {
    return status;
  }

  @Nullable Instant getRefreshedAt() {
    return refreshedAt;
  }

  byte @Nullable [] getConsentDocument() {
    return consentDocument;
  }

  Long getIdOrThrow() {
    return Objects.requireNonNull(id, "Tax data consent is not saved yet");
  }

  void awaitSignatureOf(ConsentDocument document, Instant at) {
    clear();
    status = AWAITING_SIGNATURE;
    consentConfirmReference = document.confirmReference();
    consentDocument = document.pdf();
    createdAt = at;
  }

  void sign(byte[] container) {
    if (status != AWAITING_SIGNATURE) {
      throw new IllegalStateException(
          "Tax data consent is not awaiting a signature: id=" + id + ", status=" + status);
    }
    status = SIGNED;
    signedContainer = container;
  }

  void approve(String reference) {
    if (status == APPROVED && reference.equals(consentReference)) {
      return;
    }
    clear();
    status = APPROVED;
    consentReference = reference;
  }

  void refreshed(Instant at) {
    refreshedAt = at;
    leaseUntil = null;
  }

  void invalidate() {
    clear();
    status = INVALID;
  }

  boolean isStillUsableAt(Instant now) {
    return status == SIGNED
        || status == APPROVED
        || (status == AWAITING_SIGNATURE
            && createdAt.isAfter(now.minus(CONSENT_SERVICE_SIGNATURE_WINDOW)));
  }

  boolean is(TaxDataConsentStatus status) {
    return this.status == status;
  }

  byte[] documentOrThrow() {
    return Objects.requireNonNull(
        consentDocument, "Tax data consent has no document: id=" + id + ", status=" + status);
  }

  byte[] signedContainerOrThrow() {
    return Objects.requireNonNull(
        signedContainer, "Tax data consent is not signed: id=" + id + ", status=" + status);
  }

  String confirmReferenceOrThrow() {
    return Objects.requireNonNull(
        consentConfirmReference,
        "Tax data consent has no confirm reference: id=" + id + ", status=" + status);
  }

  String consentReferenceOrThrow() {
    return Objects.requireNonNull(
        consentReference, "Tax data consent is not approved: id=" + id + ", status=" + status);
  }

  private void clear() {
    consentConfirmReference = null;
    consentDocument = null;
    signedContainer = null;
    consentReference = null;
    refreshedAt = null;
    leaseUntil = null;
  }
}
