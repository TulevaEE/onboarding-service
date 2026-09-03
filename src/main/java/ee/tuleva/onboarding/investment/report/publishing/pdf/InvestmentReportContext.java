package ee.tuleva.onboarding.investment.report.publishing.pdf;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record InvestmentReportContext(
    String fundTitle,
    String reportDate,
    List<SecuritySection> securitiesSections,
    @Nullable BigDecimal securitiesTotalCost,
    BigDecimal securitiesTotalMarketValue,
    BigDecimal securitiesTotalNavPercent,
    @Nullable BigDecimal securitiesTotalChange,
    List<InvestmentReportRow> cashRows,
    BigDecimal cashTotalMarketValue,
    BigDecimal cashTotalNavPercent,
    @Nullable BigDecimal cashTotalChange,
    BigDecimal totalAssetsMarketValue,
    @Nullable BigDecimal totalAssetsCost,
    BigDecimal totalAssetsNavPercent,
    BigDecimal fundNav) {

  public record SecuritySection(
      String heading,
      List<InvestmentReportRow> rows,
      @Nullable BigDecimal totalCost,
      BigDecimal totalMarketValue,
      BigDecimal totalNavPercent,
      @Nullable BigDecimal totalChange) {}
}
