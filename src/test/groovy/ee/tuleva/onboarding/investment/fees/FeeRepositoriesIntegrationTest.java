package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.00215"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, validFrom);

      assertThat(result).isPresent();
      assertThat(result.get().annualRate()).isEqualByComparingTo(new BigDecimal("0.00215"));
    }

    @Test
    void findValidRate_returnsRateForDateWithinValidity() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate checkDate = LocalDate.of(2025, 6, 15);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.00215"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, checkDate);

      assertThat(result).isPresent();
    }

    @Test
    void findValidRate_returnsEmptyWhenDateBeforeValidFrom() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate checkDate = LocalDate.of(2024, 12, 31);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.00215"), validFrom, null);

      var result = feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, checkDate);

      assertThat(result).isEmpty();
    }

    @Test
    void findValidRate_returnsEmptyWhenDateAfterValidTo() {
      LocalDate validFrom = LocalDate.of(2025, 1, 1);
      LocalDate validTo = LocalDate.of(2025, 6, 30);
      LocalDate checkDate = LocalDate.of(2025, 7, 1);
      insertFeeRate(TUK75, FeeType.MANAGEMENT, new BigDecimal("0.00215"), validFrom, validTo);

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
          TUK75, FeeType.MANAGEMENT, new BigDecimal("0.00215"), LocalDate.of(2025, 1, 1), null);

      var result =
          feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 6, 15));

      assertThat(result).isPresent();
      assertThat(result.get().annualRate()).isEqualByComparingTo(new BigDecimal("0.00215"));
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
      insertDepotFeeTier(0, "0.00035", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(1300000000, "0.00025", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(1650000000, "0.000225", LocalDate.of(2025, 1, 1));
      insertDepotFeeTier(2000000000, "0.00020", LocalDate.of(2025, 1, 1));
    }

    @Test
    void findRateForAum_returnsCorrectTierRate() {
      LocalDate date = LocalDate.of(2025, 1, 15);

      BigDecimal rateForSmallAum =
          depotFeeTierRepository.findRateForAum(new BigDecimal("500000000"), date);
      assertThat(rateForSmallAum).isEqualByComparingTo(new BigDecimal("0.00035"));

      BigDecimal rateFor1300M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("1300000000"), date);
      assertThat(rateFor1300M).isEqualByComparingTo(new BigDecimal("0.00025"));

      BigDecimal rateFor1650M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("1650000000"), date);
      assertThat(rateFor1650M).isEqualByComparingTo(new BigDecimal("0.000225"));

      BigDecimal rateFor2000M =
          depotFeeTierRepository.findRateForAum(new BigDecimal("2000000000"), date);
      assertThat(rateFor2000M).isEqualByComparingTo(new BigDecimal("0.00020"));
    }

    @Test
    void findRateForAum_returnsDefaultWhenNoTierMatches() {
      LocalDate futureDate = LocalDate.of(2099, 1, 1);
      jdbcClient.sql("DELETE FROM investment_depot_fee_tier").update();

      BigDecimal rate =
          depotFeeTierRepository.findRateForAum(new BigDecimal("1000000000"), futureDate);

      assertThat(rate).isEqualByComparingTo(new BigDecimal("0.00035"));
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
              .annualRate(new BigDecimal("0.00215"))
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
              .annualRate(new BigDecimal("0.00215"))
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
              .annualRate(new BigDecimal("0.00215"))
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
  }
}
