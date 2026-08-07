package ee.tuleva.onboarding.investment.check.fee;

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
@Table(name = "investment_fee_check_event")
@AllArgsConstructor
@NoArgsConstructor
class FeeCheckEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private LocalDate checkDate;

  // Null for the daily legs, which walk a window spanning two fee months. The monthly legs set it,
  // which is what gives each month its own severity-transition history in the notifier.
  private @Nullable LocalDate feeMonth;

  @NotNull
  @Enumerated(STRING)
  private FeeCheckType checkType;

  @NotNull
  @Enumerated(STRING)
  private FeeCheckScope feeScope;

  @NotNull
  @Enumerated(STRING)
  private FeeCheckSeverity severity;

  @NotNull private boolean deviationFound;

  private @Nullable BigDecimal deviationAmount;

  // Set when the run's Slack message could not be delivered, so this row is skipped when looking
  // for the previous severity and the deviation alerts again next run instead of going silent.
  @NotNull private boolean alertFailed;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> result = Map.of();

  private @Nullable Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
