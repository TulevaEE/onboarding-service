package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_risk_indicator_digest")
@AllArgsConstructor
@NoArgsConstructor
class RiskIndicatorDigest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @NotNull private LocalDate digestMonth;

  @NotNull @Builder.Default private Boolean complete = true;

  private @Nullable Instant sentAt;

  @PrePersist
  protected void onCreate() {
    if (sentAt == null) {
      sentAt = clock().instant();
    }
  }
}
