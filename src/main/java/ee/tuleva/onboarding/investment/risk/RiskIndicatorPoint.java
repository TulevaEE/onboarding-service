package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_risk_indicator_point")
@AllArgsConstructor
@NoArgsConstructor
class RiskIndicatorPoint {

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

  @NotNull private LocalDate asOfDate;

  @NotNull private String sourceKeys;

  private @Nullable Integer riskClass;

  @NotNull private Integer observationCount;

  private @Nullable BigDecimal volatility;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> metrics = Map.of();

  private @Nullable Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
