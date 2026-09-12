package ee.tuleva.onboarding.banking.check.payment;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "payment_check_event")
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCheckEvent {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  @NotNull
  @Enumerated(STRING)
  private PaymentCheckType checkType;

  @NotNull
  @Enumerated(STRING)
  private PaymentCheckSeverity severity;

  @NotNull private String externalKey;

  /** Check and field names only — never a client's name, IBAN or amount. */
  @NotNull private String detail;

  private boolean alertFailed;

  @NotNull private Instant createdAt;
}
