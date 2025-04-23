package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import ee.tuleva.onboarding.epis.transaction.TransactionFundBalanceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class FundBalanceFixture {

  public static final String ISIN_1 = "EE3600109435";
  public static final String ISIN_2 = "EE3600001707";
  public static final LocalDate DATE_1 = LocalDate.of(2025, 4, 20);
  public static final LocalDate DATE_2 = LocalDate.of(2025, 4, 21);
  public static final String FUND_MANAGER = "Tuleva";

  public static TransactionFundBalanceDto.TransactionFundBalanceDtoBuilder dtoBuilder() {
    return TransactionFundBalanceDto.builder()
        .isin(ISIN_1)
        .securityName("Tuleva II Samba Pensionifond")
        .nav(BigDecimal.valueOf(1.2345))
        .balance(BigDecimal.valueOf(1000000.50))
        .countInvestors(1500)
        .countUnits(BigDecimal.valueOf(810000.1234))
        .countUnitsBron(BigDecimal.valueOf(1000.0))
        .countUnitsFree(BigDecimal.valueOf(800000.1234))
        .countUnitsArest(BigDecimal.valueOf(5000.0))
        .countUnitsFM(BigDecimal.valueOf(4000.0))
        .fundManager(FUND_MANAGER)
        .requestDate(DATE_1);
  }

  public static FundBalance.FundBalanceBuilder entityBuilder(LocalDateTime created) {
    return FundBalance.builder()
        .isin(ISIN_1)
        .securityName("Tuleva II Samba Pensionifond")
        .nav(BigDecimal.valueOf(1.2345))
        .balance(BigDecimal.valueOf(1000000.50))
        .countInvestors(1500)
        .countUnits(BigDecimal.valueOf(810000.1234))
        .countUnitsBron(BigDecimal.valueOf(1000.0))
        .countUnitsFree(BigDecimal.valueOf(800000.1234))
        .countUnitsArest(BigDecimal.valueOf(5000.0))
        .countUnitsFm(BigDecimal.valueOf(4000.0))
        .fundManager(FUND_MANAGER)
        .requestDate(DATE_1)
        .dateCreated(created);
  }
}
