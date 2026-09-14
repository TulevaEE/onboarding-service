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

  @NotNull private String endToEndId;

  @NotNull
  @Enumerated(STRING)
  private OutgoingPaymentType paymentType;

  private @Nullable UUID sourceId;

  private @Nullable UUID batchId;

  @NotNull private String remitterIban;

  @NotNull private String beneficiaryIban;

  @NotNull
  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  @NotNull private String currency;

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
