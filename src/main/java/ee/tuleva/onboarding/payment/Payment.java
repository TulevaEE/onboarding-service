package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static javax.persistence.EnumType.*;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  @NotNull
  @Enumerated(STRING)
  private Currency currency = EUR;

  @NotNull
  @Enumerated(STRING)
  private PaymentStatus status;

  private Instant createdTime;

  @PrePersist
  protected void onCreate() {
    createdTime = Instant.now();
  }
}
