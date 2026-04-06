package ee.tuleva.onboarding.investment.check.tracking;

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

@Data
@Builder
@Entity
@Table(name = "investment_tracking_difference_event")
@AllArgsConstructor
@NoArgsConstructor
class TrackingDifferenceEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private LocalDate checkDate;

  @NotNull
  @Enumerated(STRING)
  private TrackingCheckType checkType;

  @NotNull private BigDecimal trackingDifference;

  @NotNull private BigDecimal fundReturn;

  @NotNull private BigDecimal benchmarkReturn;

  @NotNull private boolean breach;

  private int consecutiveBreachDays;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> result = Map.of();

  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
