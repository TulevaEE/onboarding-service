package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({FeeRateRepository.class, DepotFeeTierRepository.class, FeeAccrualRepository.class})
class FeeRepositoriesIntegrationTest {

  @Autowired private JdbcClient jdbcClient;

  @Nested
  class FeeRateRepositoryTests {

    @Autowired private FeeRateRepository feeRateRepository;

    @BeforeEach
    void setUp() {
      jdbcClient.sql("DELETE FROM investment_fee_rate").update();
    }

    @Test
    void findValidRate_returnsRateForExactDate() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.02"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, validFrom);

      assertThat(result).isPresent();
      assertThat(result.get().annualRate()).isEqualByComparingTo(new BigDecimal("0.02"));
    }

    @Test
    void findValidRate_returnsRateForDateWithinValidity() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate checkDate = LocalDate.of(2025, 6, 15);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.02"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, checkDate);

      assertThat(result).isPresent();
    }

    @Test
    void findValidRate_returnsEmptyWhenDateBeforeValidFrom() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate checkDate = LocalDate.of(2024, 12, 31);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.02"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, checkDate);

      assertThat(result).isEmpty();
    }

    @Test
    void findValidRate_returnsEmptyWhenDateAfterValidTo() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate validTo = LocalDate.of(2025, 6, 30);
      LocalDate checkDate = LocalDate.of(2025, 7, 1);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.02"), validFrom, validTo);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, checkDate);

      assertThat(result).isEmpty();
    }

    @Test
    void findValidRate_returnsMostRecentWhenMultipleRatesExist() {
      insertFeeRate(
          TUK75,
          FeeType.MANAGEMENT,
          new BigDecimal("0.00200"),
          LocalDate.of(2024, 1, 1),
          LocalDate.of(2024, 12, 31));
      insertFeeRate(
          TUK75, FeeType.MANAGEMENT, new BigDecimal("0.02"), LocalDate.of(2025, 1, 1), null);

      var result =
          feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 6, 15));

      assertThat(result).isPresent();
      assertThat(result.get().annualRate()).isEqualByComparingTo(new BigDecimal("0.02"));
    }

    private void insertFeeRate(
        TulevaFund fund,
        FeeType feeType,
        BigDecimal annualRate,
        LocalDate validFrom,
        LocalDate validTo) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, valid_to, created_by)
              VALUES (:fundCode, :feeType, :annualRate, :validFrom, :validTo, 'TEST')
              """)
          .param("fundCode", fund.name())
          .param("feeType", feeType.name())
          .param("annualRate", annualRate)
          .param("validFrom", validFrom)
          .param("validTo", validTo)
          .update();
    }
  }

  @Nested
  class DepotFeeTierRepositoryTests {

    @Autowired private DepotFeeTierRepository depotFeeTierRepository;

    @BeforeEach
    void setUp() {
      jdbcClient.sql("DELETE FROM investment_depot_fee_tier").update();
      insertDepotFeeTier(0, "0.01", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(1300000000, "0.005", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(1650000000, "0.0025", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(2000000000, "0.001", LocalDate.of(2025, 1, 1));
    }

    @Test
    void findRateForAum_returnsCorrectTierRate() {
      LocalDate date = LocalDate.of(2025, 1, 15);

      BigDecimal rateForSmallAum =
          depotFeeTierRepository.findRateForAum(new BigDecimal("500000000"), date);
      assertThat(rateForSmallAum).isEqualByComparingTo(new BigDecimal("0.01"));

      BigDecimal rateFor1300M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("1300000000"), date);
      assertThat(rateFor1300M).isEqualByComparingTo(new BigDecimal("0.005"));

      BigDecimal rateFor1650M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("1650000000"), date);
      assertThat(rateFor1650M).isEqualByComparingTo(new BigDecimal("0.0025"));

      BigDecimal rateFor2000M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("2000000000"), date);
      assertThat(rateFor2000M).isEqualByComparingTo(new BigDecimal("0.001"));
    }

    @Test
    void findRateForAum_throwsWhenNoTierMatches() {
      LocalDate futureDate = LocalDate.of(2099, 1, 1);
      jdbcClient.sql("DELETE FROM investment_depot_fee_tier").update();

      BigDecimal totalAum = new BigDecimal("1000000000");
      assertThatThrownBy(() -> depotFeeTierRepository.findRateForAum(totalAum, futureDate))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No depot fee tier found")
          .hasMessageContaining("totalAum=" + totalAum)
          .hasMessageContaining("date=" + futureDate);
    }

    private void insertDepotFeeTier(long minAum, String annualRate, LocalDate validFrom) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_depot_fee_tier (min_aum, annual_rate, valid_from)
              VALUES (:minAum, :annualRate, :validFrom)
              """)
          .param("minAum", minAum)
          .param("annualRate", new BigDecimal(annualRate))
          .param("validFrom", validFrom)
          .update();
    }
  }

  @Nested
  class FeeAccrualRepositoryTests {

    @Autowired private FeeAccrualRepository feeAccrualRepository;

    @BeforeEach
    void setUp() {
      jdbcClient.sql("DELETE FROM investment_fee_accrual").update();
    }

    @Test
    void save_insertsAccrual() {
      FeeAccrual accrual =
          FeeAccrual.builder()
              .fund(TUK75)
              .feeType(FeeType.MANAGEMENT)
              .accrualDate(LocalDate.of(2025, 1, 15))
              .feeMonth(LocalDate.of(2025, 1, 1))
              .baseValue(BigDecimal.valueOf(1000000))
              .annualRate(new BigDecimal("0.02"))
              .dailyAmountNet(BigDecimal.TEN)
              .dailyAmountGross(BigDecimal.TEN)
              .daysInYear(365)
              .build();

      feeAccrualRepository.save(accrual);

      BigDecimal dailyAmountNet =
          jdbcClient
              .sql(
                  """
                  SELECT daily_amount_net FROM investment_fee_accrual
                  WHERE fund_code = :fundCode AND fee_type = :feeType AND accrual_date = :accrualDate
                  """)
              .param("fundCode", TUK75.name())
              .param("feeType", FeeType.MANAGEMENT.name())
              .param("accrualDate", LocalDate.of(2025, 1, 15))
              .query(BigDecimal.class)
              .single();

      assertThat(dailyAmountNet).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void getAccruedFeeAsOf_sumsThenRounds() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.891"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 14), new BigDecimal("5.892"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15), new BigDecimal("5.893"));

      BigDecimal result =
          feeAccrualRepository.getAccruedFeeAsOf(
              TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15));

      // ROUND(5.891 + 5.892 + 5.893, 2) = ROUND(17.676, 2) = 17.68
      // NOT SUM(ROUND(each, 2)) = 5.89 + 5.89 + 5.89 = 17.67
      assertThat(result).isEqualByComparingTo(new BigDecimal("17.68"));
    }

    @Test
    void getUnsettledAccrual_sumsThenRounds() {
      LocalDate feeMonth = LocalDate.of(2025, 1, 1);
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), feeMonth, new BigDecimal("5.891"));
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 14), feeMonth, new BigDecimal("5.892"));
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15), feeMonth, new BigDecimal("5.893"));

      BigDecimal result =
          feeAccrualRepository.getUnsettledAccrual(
              TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15));

      assertThat(result).isEqualByComparingTo(new BigDecimal("17.68"));
    }

    @Test
    void save_updatesAccrualOnDuplicate() {
      LocalDate accrualDate = LocalDate.of(2025, 1, 15);
      LocalDate feeMonth = LocalDate.of(2025, 1, 1);

      FeeAccrual first =
          FeeAccrual.builder()
              .fund(TUK75)
              .feeType(FeeType.MANAGEMENT)
              .accrualDate(accrualDate)
              .feeMonth(feeMonth)
              .baseValue(BigDecimal.valueOf(1000000))
              .annualRate(new BigDecimal("0.02"))
              .dailyAmountNet(BigDecimal.TEN)
              .dailyAmountGross(BigDecimal.TEN)
              .daysInYear(365)
              .build();
      feeAccrualRepository.save(first);

      FeeAccrual updated =
          FeeAccrual.builder()
              .fund(TUK75)
              .feeType(FeeType.MANAGEMENT)
              .accrualDate(accrualDate)
              .feeMonth(feeMonth)
              .baseValue(BigDecimal.valueOf(2000000))
              .annualRate(new BigDecimal("0.02"))
              .dailyAmountNet(new BigDecimal("20"))
              .dailyAmountGross(new BigDecimal("20"))
              .daysInYear(365)
              .build();
      feeAccrualRepository.save(updated);

      BigDecimal dailyAmountNet =
          jdbcClient
              .sql(
                  """
                  SELECT daily_amount_net FROM investment_fee_accrual
                  WHERE fund_code = :fundCode AND fee_type = :feeType AND accrual_date = :accrualDate
                  """)
              .param("fundCode", TUK75.name())
              .param("feeType", FeeType.MANAGEMENT.name())
              .param("accrualDate", accrualDate)
              .query(BigDecimal.class)
              .single();

      assertThat(dailyAmountNet).isEqualByComparingTo(new BigDecimal("20"));
    }

    private void insertAccrual(
        TulevaFund fund, FeeType feeType, LocalDate accrualDate, BigDecimal dailyAmountNet) {
      insertAccrualWithFeeMonth(
          fund, feeType, accrualDate, accrualDate.withDayOfMonth(1), dailyAmountNet);
    }

    private void insertAccrualWithFeeMonth(
        TulevaFund fund,
        FeeType feeType,
        LocalDate accrualDate,
        LocalDate feeMonth,
        BigDecimal dailyAmountNet) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_accrual (
                  fund_code, fee_type, accrual_date, fee_month, base_value,
                  annual_rate, daily_amount_net, daily_amount_gross, days_in_year
              )
              VALUES (
                  :fundCode, :feeType, :accrualDate, :feeMonth, 1000000,
                  0.02, :dailyAmountNet, :dailyAmountNet, 365
              )
              """)
          .param("fundCode", fund.name())
          .param("feeType", feeType.name())
          .param("accrualDate", accrualDate)
          .param("feeMonth", feeMonth)
          .param("dailyAmountNet", dailyAmountNet)
          .update();
    }
  }
}
