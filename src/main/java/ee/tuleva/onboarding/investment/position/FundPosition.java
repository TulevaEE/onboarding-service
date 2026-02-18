package ee.tuleva.onboarding.investment.position;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.fund.TulevaFund;
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
@Table(name = "investment_fund_position")
@AllArgsConstructor
@NoArgsConstructor
public class FundPosition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private LocalDate reportingDate;

  @NotNull
  @Enumerated(STRING)
  @Column(name = "fund_code")
  private TulevaFund fund;

  @NotNull
  @Enumerated(STRING)
  private AccountType accountType;

  @NotNull private String accountName;

  private String accountId;

  private BigDecimal quantity;

  private BigDecimal marketPrice;

  private String currency;

  private BigDecimal marketValue;

  private Instant createdAt;
}
