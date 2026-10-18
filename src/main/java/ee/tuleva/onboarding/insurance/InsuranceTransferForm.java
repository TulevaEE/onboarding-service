package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormStatus.CREATED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormStatus.SIGNED;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "insurance_transfer_form")
@Getter
@NoArgsConstructor(access = PROTECTED)
class InsuranceTransferForm {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private Long userId;

  @Enumerated(STRING)
  private Insurer insurer;

  private String pensionAccountNumber;

  private byte @Nullable [] filledPdf;

  private byte @Nullable [] signedContainer;

  private @Nullable Instant signedAt;

  @Enumerated(STRING)
  private @Nullable InsuranceTransferEmailStatus emailStatus;

  private Instant createdAt;

  InsuranceTransferForm(
      Long userId,
      Insurer insurer,
      String pensionAccountNumber,
      byte[] filledPdf,
      Instant createdAt) {
    this.userId = userId;
    this.insurer = insurer;
    this.pensionAccountNumber = pensionAccountNumber;
    this.filledPdf = filledPdf;
    this.createdAt = createdAt;
  }

  Long getIdOrThrow() {
    return Objects.requireNonNull(id, "Insurance transfer form is not saved yet");
  }

  InsuranceTransferFormStatus getStatus() {
    return isSigned() ? SIGNED : CREATED;
  }

  boolean isSigned() {
    return signedAt != null;
  }

  void sign(byte[] container, Instant at) {
    if (isSigned()) {
      throw new IllegalStateException("Insurance transfer form is already signed: id=" + id);
    }
    signedContainer = container;
    signedAt = at;
  }

  void recordEmailStatus(InsuranceTransferEmailStatus status) {
    emailStatus = status;
  }
}
