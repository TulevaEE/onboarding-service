package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.currency.Currency.*;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @ManyToOne private User user;

  @NotNull private UUID internalReference;

  @NotNull private BigDecimal amount;

  @NotNull private Currency currency = EUR;

  @NotNull private PaymentStatus status;
  private Instant createdTime;

  @PrePersist
  protected void onCreate() {
    createdTime = Instant.now();
  }
}
