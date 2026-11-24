package ee.tuleva.onboarding.investment.fees.instrument;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class InstrumentFeeAccrualRepository {

  private final JdbcClient jdbcClient;

  @Transactional
  void replaceTheFundsDay(
      TulevaFund fund, LocalDate day, List<InstrumentFeeAccrual> accruals, Instant calculatedAt) {
    jdbcClient
        .sql(
            """
            DELETE FROM investment_instrument_fee_accrual
            WHERE fund_code = :fundCode AND accrual_date = :day
            """)
        .param("fundCode", fund.getCode())
        .param("day", day)
        .update();
    accruals.forEach(accrual -> insert(accrual, calculatedAt));
  }

  boolean holdsProvisionalAccrualsBetween(LocalDate firstDay, LocalDate lastDay) {
    return jdbcClient
            .sql(
                """
            SELECT COUNT(*) FROM investment_instrument_fee_accrual
            WHERE rate_status = 'PROVISIONAL' AND accrual_date BETWEEN :firstDay AND :lastDay
            """)
            .param("firstDay", firstDay)
            .param("lastDay", lastDay)
            .query(Long.class)
            .single()
        > 0;
  }

  private void insert(InstrumentFeeAccrual accrual, Instant calculatedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_accrual
              (fund_code, isin, accrual_date, instrument_fee_id, provider_agreement_version_id,
               accrual_method, rate_status, instrument_fee_rate_id, rebate_rate, invoiced_fee_rate,
               units, units_source, units_date, price, price_date, price_source,
               rebate_amount, invoiced_fee_amount, calculated_at)
            VALUES
              (:fundCode, :isin, :accrualDate, :instrumentFeeId, :providerAgreementVersionId,
               :accrualMethod, :rateStatus, :instrumentFeeRateId, :rebateRate, :invoicedFeeRate,
               :units, :unitsSource, :unitsDate, :price, :priceDate, :priceSource,
               :rebateAmount, :invoicedFeeAmount, :calculatedAt)
            """)
        .param("fundCode", accrual.fund().getCode())
        .param("isin", accrual.rate().isin())
        .param("accrualDate", accrual.accrualDate())
        .param("instrumentFeeId", accrual.rate().instrumentFeeId())
        .param("providerAgreementVersionId", accrual.providerAgreement().id())
        .param("accrualMethod", accrual.providerAgreement().method().name())
        .param("rateStatus", accrual.rate().status().name())
        .param("instrumentFeeRateId", accrual.rate().instrumentFeeRateId())
        .param("rebateRate", accrual.rate().rebateRate())
        .param("invoicedFeeRate", accrual.rate().invoicedFeeRate())
        .param("units", accrual.holding().units())
        .param("unitsSource", accrual.holding().source().name())
        .param("unitsDate", accrual.holding().unitsDate())
        .param("price", accrual.price().price())
        .param("priceDate", accrual.price().priceDate())
        .param("priceSource", accrual.price().source())
        .param("rebateAmount", accrual.rebateAmount())
        .param("invoicedFeeAmount", accrual.invoicedFeeAmount())
        .param("calculatedAt", Timestamp.from(calculatedAt))
        .update();
  }
}
