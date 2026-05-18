package ee.tuleva.onboarding.investment.transaction.portfolio;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "investment_portfolio_cost_basis")
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioCostBasis {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String fundIsin;

  @NotNull private String instrumentIsin;

  @NotNull private LocalDate asOfDate;

  @NotNull private BigDecimal quantity;

  @NotNull private BigDecimal avgUnitCost;

  @NotNull private BigDecimal totalCost;

  @NotNull @Builder.Default private BigDecimal deltaQuantity = BigDecimal.ZERO;

  @NotNull private String source;

  @Version private Long version;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

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
