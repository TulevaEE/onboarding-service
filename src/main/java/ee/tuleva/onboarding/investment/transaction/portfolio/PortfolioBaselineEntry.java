package ee.tuleva.onboarding.investment.transaction.portfolio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "investment_portfolio_baseline_entry")
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioBaselineEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String instrumentIsin;

  @NotNull private BigDecimal quantity;

  @NotNull private BigDecimal avgUnitCost;
}
