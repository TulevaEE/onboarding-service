package ee.tuleva.onboarding.investment.position;

import jakarta.persistence.Entity;
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
@Table(name = "investment_fund_position")
@AllArgsConstructor
@NoArgsConstructor
public class FundPosition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate reportDate;

  @NotNull private LocalDate navDate;

  @NotNull private String portfolio;

  @NotNull private String fundCode;

  @NotNull private String assetType;

  private String instrumentType;

  private String isin;

  private String securityId;

  @NotNull private String assetName;

  private String issuerName;

  private BigDecimal quantity;

  private String fundCurrency;

  private String assetCurrency;

  private BigDecimal price;

  private BigDecimal marketValue;

  private BigDecimal percentageOfNav;

  private Instant createdAt;
}
