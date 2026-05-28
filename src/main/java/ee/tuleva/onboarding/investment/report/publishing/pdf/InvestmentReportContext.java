package ee.tuleva.onboarding.investment.report.publishing.pdf;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentReportContext(
    String fundTitle,
    String reportDate,
    List<SecuritySection> securitiesSections,
    BigDecimal securitiesTotalCost,
    BigDecimal securitiesTotalMarketValue,
    BigDecimal securitiesTotalNavPercent,
    BigDecimal securitiesTotalChange,
    List<InvestmentReportRow> cashRows,
    BigDecimal cashTotalMarketValue,
    BigDecimal cashTotalNavPercent,
    BigDecimal cashTotalChange,
    BigDecimal totalAssetsMarketValue,
    BigDecimal totalAssetsCost,
    BigDecimal totalAssetsNavPercent,
    BigDecimal fundNav) {

  public record SecuritySection(
      String heading,
      List<InvestmentReportRow> rows,
      BigDecimal totalCost,
      BigDecimal totalMarketValue,
      BigDecimal totalNavPercent,
      BigDecimal totalChange) {}
}
