package ee.tuleva.onboarding.investment.portfolio;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.investment.TulevaFund;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@Entity
@Table(name = "investment_model_portfolio_allocation")
@AllArgsConstructor
@NoArgsConstructor
public class ModelPortfolioAllocation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate effectiveDate;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @Nullable private String isin;

  @Nullable private String ticker;

  @NotNull private BigDecimal weight;

  @Nullable private String label;

  @Enumerated(STRING)
  @Nullable
  private Provider provider;

  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }
}
