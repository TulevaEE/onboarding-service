package ee.tuleva.onboarding.investment.transaction;

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
@Table(name = "investment_transaction_command")
@AllArgsConstructor
@NoArgsConstructor
public class TransactionCommand {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull
  @Enumerated(STRING)
  private TransactionMode mode;

  @NotNull private LocalDate asOfDate;

  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> manualAdjustments = Map.of();

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private CommandStatus status = CommandStatus.PENDING;

  private String errorMessage;

  private Long batchId;

  private Instant createdAt;

  private Instant processedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
