package ee.tuleva.onboarding.epis.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFundBalanceDto {
  private String securityName;
  private String isin;
  private BigDecimal nav;
  private BigDecimal balance;
  private Integer countInvestors;
  private BigDecimal countUnits;
  private BigDecimal countUnitsBron;
  private BigDecimal countUnitsFree;
  private BigDecimal countUnitsArest;
  private BigDecimal countUnitsFM;
  private String fundManager;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private LocalDate requestDate;
}
