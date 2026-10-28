package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_transaction_execution")
@AllArgsConstructor
@NoArgsConstructor
public class TransactionExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(name = "order_id")
  private Long orderId;

  @Nullable private String brokerTransactionId;

  @Nullable private UUID aggregatedOrderId;

  @Nullable private Instant executionTimestamp;

  @Nullable private BigDecimal executedQuantity;

  @Nullable private BigDecimal unitPrice;

  @Nullable private BigDecimal totalConsideration;

  @Nullable private BigDecimal settlementAmount;

  @Nullable private BigDecimal commissionAmount;

  @Nullable private BigDecimal settlementFeeAmount;

  @Nullable private BigDecimal settlementPenalty;

  @Nullable private BigDecimal netSettlementAmount;

  @Nullable private LocalDate scheduledSettlementDate;

  @Nullable private LocalDate reportedDate;

  private @Nullable LocalDate navDate;

  private @Nullable String comment;

  @NotNull private String source;

  private @Nullable String sourceFileKey;

  private @Nullable String modifiedBy;

  private Instant createdAt;

  private Instant updatedAt;

  @Version private Long version;

  @PrePersist
  protected void onCreate() {
    Instant now = clock().instant();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = clock().instant();
  }
}
