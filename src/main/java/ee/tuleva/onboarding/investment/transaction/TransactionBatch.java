package ee.tuleva.onboarding.investment.transaction;

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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@Builder
@Entity
@Table(name = "investment_transaction_batch")
@AllArgsConstructor
@NoArgsConstructor
public class TransactionBatch {

  public static final String TRADE_REGISTRY_SQL_IMPORT_SOURCE = "GAS_REGISTRY";
  public static final String HISTORICAL_IMPORT_SOURCE = "HISTORICAL_IMPORT";
  private static final Set<String> TRADE_REGISTRY_IMPORT_SOURCES =
      Set.of(TRADE_REGISTRY_SQL_IMPORT_SOURCE, HISTORICAL_IMPORT_SOURCE);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private String createdBy;

  @NotNull
  @Enumerated(STRING)
  @Builder.Default
  private BatchStatus status = BatchStatus.DRAFT;

  @NotNull
  @Builder.Default
  @JdbcTypeCode(JSON)
  private Map<String, Object> metadata = Map.of();

  private Instant createdAt;

  private String confirmedBy;

  private Instant confirmedAt;

  private String cancellationReason;

  private String cancelledBy;

  private Instant cancelledAt;

  @Version private Long version;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = clock().instant();
    }
  }

  public boolean importedFromTheTradeRegistry() {
    return metadata.get("source") instanceof String source
        && TRADE_REGISTRY_IMPORT_SOURCES.contains(source);
  }
}
