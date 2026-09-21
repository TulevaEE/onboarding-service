package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;
import static org.hibernate.type.SqlTypes.JSON;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  static final String FINGERPRINT = "fingerprint";

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

  // Skips this row when the next run looks for the previous severity, so a deviation first seen
  // during a Slack outage alerts again rather than going silent for good.
  @NotNull private boolean alertFailed;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> result = Map.of();

  private @Nullable Instant createdAt;

  // Rows written before the notifier compared finding sets have no fingerprint, so they read as an
  // empty set and the first run after them announces whatever those checks are still reporting.
  List<String> fingerprint() {
    return result.get(FINGERPRINT) instanceof List<?> stored
        ? stored.stream().map(String::valueOf).toList()
        : List.of();
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
