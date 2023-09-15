package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.payment.PaymentData.*;
import static javax.persistence.EnumType.STRING;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import javax.persistence.*;
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
public class Payment implements Comparable<Payment> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @ManyToOne private User user;

  @NotNull private UUID internalReference;

  @NotNull private BigDecimal amount;

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private Currency currency = EUR;

  @NotNull private String recipientPersonalCode;

  private Instant createdTime;

  @NotNull
  @Enumerated(STRING)
  private PaymentType paymentType;

  @PrePersist
  protected void onCreate() {
    createdTime = Instant.now();
  }

  @Override
  public int compareTo(@org.jetbrains.annotations.NotNull Payment other) {
    return Comparator.comparing(Payment::getCreatedTime, Comparator.nullsLast(Instant::compareTo))
        .thenComparing(Payment::getAmount)
        .thenComparing(Payment::getCurrency)
        .thenComparing(Payment::getRecipientPersonalCode)
        .thenComparing(Payment::getInternalReference)
        .thenComparing(Payment::getId, Comparator.nullsLast(Long::compareTo))
        .thenComparing(Payment::getPaymentType)
        .compare(this, other);
  }
}
