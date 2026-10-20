package ee.tuleva.onboarding.payment.recurring;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "third_pillar_recurring_payment_confirmation")
@NoArgsConstructor(access = PROTECTED)
class RecurringPaymentConfirmation {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private String personalCode;

  private Instant confirmedAt;

  RecurringPaymentConfirmation(String personalCode, Instant confirmedAt) {
    this.personalCode = personalCode;
    this.confirmedAt = confirmedAt;
  }
}
