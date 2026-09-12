package ee.tuleva.onboarding.banking.payment;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** One payment we sent to the bank, recorded before the call rather than after it. */
@Data
@Builder
@Entity
@Table(name = "outgoing_payment")
@AllArgsConstructor
@NoArgsConstructor
public class OutgoingPayment {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  /** Also the bank's Idempotency-Key, which is why this column is unique. */
  @NotNull private String endToEndId;

  @NotNull
  @Enumerated(STRING)
  private OutgoingPaymentType paymentType;

  /** The redemption request, saving fund payment or batch this payment came from. */
  private @Nullable UUID sourceId;

  /** Shared by a redemption transfer and the payouts it funds. */
  private @Nullable UUID batchId;

  @NotNull private String remitterIban;

  @NotNull private String beneficiaryIban;

  @NotNull
  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  @NotNull private String currency;

  /** SHA-256 of the exact bytes submitted, so the file can be tied back to this row. */
  @NotNull private String bodyHash;

  @NotNull
  @Enumerated(STRING)
  private OutgoingPaymentStatus status;

  private @Nullable String failureReason;

  @NotNull private Instant attemptedAt;

  private @Nullable Instant resolvedAt;

  public boolean isPending() {
    return status == OutgoingPaymentStatus.ATTEMPTED;
  }
}
