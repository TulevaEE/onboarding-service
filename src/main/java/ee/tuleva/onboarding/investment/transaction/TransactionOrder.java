package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "investment_transaction_order")
@AllArgsConstructor
@NoArgsConstructor
public class TransactionOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "batch_id", nullable = false)
  private TransactionBatch batch;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private String instrumentIsin;

  @NotNull
  @Enumerated(STRING)
  private TransactionType transactionType;

  @NotNull
  @Enumerated(STRING)
  private InstrumentType instrumentType;

  private BigDecimal orderAmount;

  private Long orderQuantity;

  @NotNull
  @Enumerated(STRING)
  private OrderVenue orderVenue;

  private String traderId;

  @NotNull @Builder.Default private UUID orderUuid = UUID.randomUUID();

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private OrderStatus orderStatus = OrderStatus.PENDING;

  @NotNull @Builder.Default private String orderType = "MOC";

  private Instant orderTimestamp;

  private LocalDate expectedSettlementDate;

  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }
}
