package ee.tuleva.onboarding.investment.fees.ocf;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

final class OcfSnapshotParameters {

  private OcfSnapshotParameters() {}

  static StatementSpec bind(StatementSpec spec, OcfSnapshot snapshot) {
    var audit = snapshot.audit();
    return spec.param("fundCode", snapshot.fundCode())
        .param("snapshotMonth", snapshot.snapshotMonth())
        .param("managementFeeRate", snapshot.managementFeeRate())
        .param("depotFeeRate", snapshot.depotFeeRate())
        .param("underlyingFundCost", snapshot.underlyingFundCost())
        .param("underlyingFundCostGross", snapshot.underlyingFundCostGross())
        .param("underlyingFundCostNet", snapshot.underlyingFundCostNet())
        .param("rebateBasis", snapshot.rebateBasis().name())
        .param("methodology", snapshot.methodology().name())
        .param("transactionCostRate", snapshot.transactionCostRate())
        .param("totalOcf", snapshot.totalOcf())
        .param("complete", snapshot.complete())
        .param("checks", snapshot.checks() == null ? "{}" : snapshot.checks())
        .param("navDate", audit.navDate())
        .param("navCalculationId", audit.navCalculationId())
        .param("assetsUnderManagement", audit.assetsUnderManagement())
        .param("managementFeeRateId", audit.managementFeeRateId())
        .param("depotChargedToFund", audit.depotChargedToFund())
        .param("depotTierNavDate", audit.depotTierNavDate())
        .param("depotTierBasis", audit.depotTierBasis())
        .param("txnWindowStart", audit.txnWindowStart())
        .param("txnWindowEnd", audit.txnWindowEnd())
        .param("txnFees", audit.txnFees())
        .param("txnAverageAum", audit.txnAverageAum())
        .param("txnNavDates", audit.txnNavDates())
        .param("underlyingWeightBasis", nameOf(audit.underlyingWeightBasis()))
        .param("investedRatioMonthEnd", audit.investedRatioMonthEnd())
        .param("investedRatioMonthAverage", audit.investedRatioMonthAverage());
  }

  private static @Nullable String nameOf(@Nullable UnderlyingWeightBasis basis) {
    return basis == null ? null : basis.name();
  }
}
