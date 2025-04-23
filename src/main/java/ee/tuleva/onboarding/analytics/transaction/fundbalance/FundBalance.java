package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "fund_balance")
public class FundBalance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String securityName;
  private String isin;
  private BigDecimal nav;
  private BigDecimal balance;
  private Integer countInvestors;
  private BigDecimal countUnits;
  private BigDecimal countUnitsBron;
  private BigDecimal countUnitsFree;
  private BigDecimal countUnitsArest;
  private BigDecimal countUnitsFm;
  private String fundManager;
  private LocalDate requestDate;
  private LocalDateTime dateCreated;
}
