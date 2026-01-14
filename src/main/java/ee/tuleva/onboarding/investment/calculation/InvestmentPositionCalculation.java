package ee.tuleva.onboarding.investment.calculation;

import static jakarta.persistence.EnumType.STRING;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "investment_position_calculation")
@AllArgsConstructor
@NoArgsConstructor
public class InvestmentPositionCalculation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String isin;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull private LocalDate date;

  @NotNull private BigDecimal quantity;

  @Column(name = "eodhd_price")
  private BigDecimal eodhdPrice;

  @Column(name = "yahoo_price")
  private BigDecimal yahooPrice;

  @Column(name = "used_price")
  private BigDecimal usedPrice;

  @Enumerated(STRING)
  @Column(name = "price_source")
  private PriceSource priceSource;

  @Column(name = "calculated_market_value")
  private BigDecimal calculatedMarketValue;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "validation_status")
  private ValidationStatus validationStatus;

  @Column(name = "price_discrepancy_percent")
  private BigDecimal priceDiscrepancyPercent;

  @Column(name = "created_at")
  private Instant createdAt;
}
