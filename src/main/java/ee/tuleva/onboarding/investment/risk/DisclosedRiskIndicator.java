package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
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

/**
 * The risk class actually printed in the filed KID/KIID document. Maintained by hand, one row per
 * document version — the comparison against the computed class is the whole point of the check.
 */
@Data
@Builder
@Entity
@Table(name = "investment_risk_indicator_disclosure")
@AllArgsConstructor
@NoArgsConstructor
class DisclosedRiskIndicator {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @NotNull
  @Enumerated(STRING)
  private RiskIndicatorType indicatorType;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private Integer disclosedClass;

  @NotNull private LocalDate disclosedFrom;

  @NotNull private String document;

  private @Nullable String note;

  private @Nullable Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
