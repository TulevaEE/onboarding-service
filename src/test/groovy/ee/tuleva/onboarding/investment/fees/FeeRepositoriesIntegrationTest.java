package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  FeeRateRepository.class,
  DepotFeeTierRepository.class,
  FeeAccrualRepository.class,
  FeeChargedToFundPolicy.class
})
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

      assertThat(depotFeeTierRepository.findRateForAum(new BigDecimal("500000000"), date))
          .get(BIG_DECIMAL)
          .isEqualByComparingTo(new BigDecimal("0.01"));
      assertThat(depotFeeTierRepository.findRateForAum(new BigDecimal("1300000000"), date))
          .get(BIG_DECIMAL)
          .isEqualByComparingTo(new BigDecimal("0.005"));
      assertThat(depotFeeTierRepository.findRateForAum(new BigDecimal("1650000000"), date))
          .get(BIG_DECIMAL)
          .isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(depotFeeTierRepository.findRateForAum(new BigDecimal("2000000000"), date))
          .get(BIG_DECIMAL)
          .isEqualByComparingTo(new BigDecimal("0.001"));
    }

    @Test
    void findRateForAum_isEmptyWhenNoTierMatches() {
      LocalDate futureDate = LocalDate.of(2099, 1, 1);
      jdbcClient.sql("DELETE FROM investment_depot_fee_tier").update();

      assertThat(depotFeeTierRepository.findRateForAum(new BigDecimal("1000000000"), futureDate))
          .isEmpty();
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
              .dailyAmountGross(BigDecimal.TEN)
              .daysInYear(365)
              .build();

      feeAccrualRepository.save(accrual);

      BigDecimal dailyAmountGross =
          jdbcClient
              .sql(
                  """
                  SELECT daily_amount_gross FROM investment_fee_accrual
                  WHERE fund_code = :fundCode AND fee_type = :feeType AND accrual_date = :accrualDate
                  """)
              .param("fundCode", TUK75.name())
              .param("feeType", FeeType.MANAGEMENT.name())
              .param("accrualDate", LocalDate.of(2025, 1, 15))
              .query(BigDecimal.class)
              .single();

      assertThat(dailyAmountGross).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void save_writesTheSameAmountToTheColumnTheOlderImageStillReads() {
      FeeAccrual accrual =
          FeeAccrual.builder()
              .fund(TUK75)
              .feeType(FeeType.MANAGEMENT)
              .accrualDate(LocalDate.of(2025, 1, 15))
              .feeMonth(LocalDate.of(2025, 1, 1))
              .baseValue(BigDecimal.valueOf(1000000))
              .annualRate(new BigDecimal("0.02"))
              .dailyAmountGross(new BigDecimal("5.894"))
              .daysInYear(365)
              .build();

      feeAccrualRepository.save(accrual);

      assertThat(legacyNetAmountOn(LocalDate.of(2025, 1, 15)))
          .isEqualByComparingTo(new BigDecimal("5.894"));
    }

    @Test
    void save_keepsTheLegacyColumnInStepWhenItUpdatesAnExistingDay() {
      LocalDate accrualDate = LocalDate.of(2025, 1, 15);
      insertAccrual(TUK75, FeeType.MANAGEMENT, accrualDate, new BigDecimal("5.894"));

      feeAccrualRepository.save(
          FeeAccrual.builder()
              .fund(TUK75)
              .feeType(FeeType.MANAGEMENT)
              .accrualDate(accrualDate)
              .feeMonth(LocalDate.of(2025, 1, 1))
              .baseValue(BigDecimal.valueOf(2000000))
              .annualRate(new BigDecimal("0.02"))
              .dailyAmountGross(new BigDecimal("11.788"))
              .daysInYear(365)
              .build());

      assertThat(legacyNetAmountOn(accrualDate)).isEqualByComparingTo(new BigDecimal("11.788"));
    }

    private BigDecimal legacyNetAmountOn(LocalDate accrualDate) {
      return jdbcClient
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
    }

    // Left unrounded per day on purpose: the caller drops the days the policy does not charge and
    // rounds what is left, so rounding here would round days that never reach the total.
    @Test
    void getUnsettledAccrualByDate_returnsEachDayUnroundedAndStillSumsThenRoundsTo17_68() {
      LocalDate feeMonth = LocalDate.of(2025, 1, 1);
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), feeMonth, new BigDecimal("5.891"));
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 14), feeMonth, new BigDecimal("5.892"));
      insertAccrualWithFeeMonth(
          TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15), feeMonth, new BigDecimal("5.893"));

      var byDate =
          feeAccrualRepository.getUnsettledAccrualByDate(
              TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 15));

      assertThat(byDate)
          .containsOnlyKeys(
              LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 14), LocalDate.of(2025, 1, 15));
      assertThat(byDate.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
          .isEqualByComparingTo(new BigDecimal("17.676"));
      assertThat(
              byDate.values().stream()
                  .reduce(BigDecimal.ZERO, BigDecimal::add)
                  .setScale(2, java.math.RoundingMode.HALF_UP))
          .isEqualByComparingTo(new BigDecimal("17.68"));
    }

    @Test
    void findRoundedDailyGrossBetween_returnsOneRoundedAmountPerDay() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.894"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 14), new BigDecimal("5.895"));
      insertAccrual(TUK75, FeeType.DEPOT, LocalDate.of(2025, 1, 14), new BigDecimal("1.111"));

      var amounts =
          feeAccrualRepository.findRoundedDailyGrossBetween(
              TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 14));

      assertThat(amounts)
          .containsExactly(
              new DailyAccrualAmount(LocalDate.of(2025, 1, 13), new BigDecimal("5.89")),
              new DailyAccrualAmount(LocalDate.of(2025, 1, 14), new BigDecimal("5.90")));
    }

    @Test
    void findBaseValuesBetween_returnsBaseValuePerDayAndFeeType() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.89"));
      insertAccrual(TUK75, FeeType.DEPOT, LocalDate.of(2025, 1, 13), new BigDecimal("1.11"));

      var baseValues =
          feeAccrualRepository.findBaseValuesBetween(
              TUK75, LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 13));

      assertThat(baseValues)
          .containsExactlyInAnyOrder(
              new FeeBaseValue(
                  LocalDate.of(2025, 1, 13), FeeType.MANAGEMENT, new BigDecimal("1000000.00")),
              new FeeBaseValue(
                  LocalDate.of(2025, 1, 13), FeeType.DEPOT, new BigDecimal("1000000.00")));
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
              .dailyAmountGross(new BigDecimal("20"))
              .daysInYear(365)
              .build();
      feeAccrualRepository.save(updated);

      BigDecimal dailyAmountGross =
          jdbcClient
              .sql(
                  """
                  SELECT daily_amount_gross FROM investment_fee_accrual
                  WHERE fund_code = :fundCode AND fee_type = :feeType AND accrual_date = :accrualDate
                  """)
              .param("fundCode", TUK75.name())
              .param("feeType", FeeType.MANAGEMENT.name())
              .param("accrualDate", accrualDate)
              .query(BigDecimal.class)
              .single();

      assertThat(dailyAmountGross).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void findByFundAndAccrualDateAndFeeType_returnsTheOneAccrualForThatDay() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.894"));
      insertAccrual(TUK75, FeeType.DEPOT, LocalDate.of(2025, 1, 13), new BigDecimal("1.111"));

      var accrual =
          feeAccrualRepository
              .findByFundAndAccrualDateAndFeeType(
                  TUK75, LocalDate.of(2025, 1, 13), FeeType.MANAGEMENT)
              .orElseThrow();

      assertThat(accrual.fund()).isEqualTo(TUK75);
      assertThat(accrual.feeType()).isEqualTo(FeeType.MANAGEMENT);
      assertThat(accrual.accrualDate()).isEqualTo(LocalDate.of(2025, 1, 13));
      assertThat(accrual.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
      assertThat(accrual.dailyAmountGross()).isEqualByComparingTo(new BigDecimal("5.894"));
      assertThat(accrual.daysInYear()).isEqualTo(365);
    }

    @Test
    void findByFundAndAccrualDateAndFeeType_isEmptyWhenTheDayHasNoAccrual() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.894"));

      assertThat(
              feeAccrualRepository.findByFundAndAccrualDateAndFeeType(
                  TUK75, LocalDate.of(2025, 1, 14), FeeType.MANAGEMENT))
          .isEmpty();
    }

    @Test
    void findByFundAndDateRange_returnsEveryFeeTypeInTheWindowOrderedByDate() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 12), new BigDecimal("5.891"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.892"));
      insertAccrual(TUK75, FeeType.DEPOT, LocalDate.of(2025, 1, 13), new BigDecimal("1.111"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 20), new BigDecimal("5.893"));

      var accruals =
          feeAccrualRepository.findByFundAndDateRange(
              TUK75, LocalDate.of(2025, 1, 12), LocalDate.of(2025, 1, 13));

      assertThat(accruals)
          .extracting(FeeAccrual::accrualDate, FeeAccrual::feeType)
          .containsExactly(
              tuple(LocalDate.of(2025, 1, 12), FeeType.MANAGEMENT),
              tuple(LocalDate.of(2025, 1, 13), FeeType.DEPOT),
              tuple(LocalDate.of(2025, 1, 13), FeeType.MANAGEMENT));
    }

    @Test
    void deleteByFundFromDate_removesFromThatDayOnAndLeavesEarlierDaysAndOtherFunds() {
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 12), new BigDecimal("5.891"));
      insertAccrual(TUK75, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 13), new BigDecimal("5.892"));
      insertAccrual(TUK75, FeeType.DEPOT, LocalDate.of(2025, 1, 14), new BigDecimal("1.111"));
      insertAccrual(TUK00, FeeType.MANAGEMENT, LocalDate.of(2025, 1, 14), new BigDecimal("2.222"));

      int deleted = feeAccrualRepository.deleteByFundFromDate(TUK75, LocalDate.of(2025, 1, 13));

      assertThat(deleted).isEqualTo(2);
      assertThat(
              feeAccrualRepository.findByFundAndDateRange(
                  TUK75, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
          .extracting(FeeAccrual::accrualDate)
          .containsExactly(LocalDate.of(2025, 1, 12));
      assertThat(
              feeAccrualRepository.findByFundAndDateRange(
                  TUK00, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
          .hasSize(1);
    }

    private void insertAccrual(
        TulevaFund fund, FeeType feeType, LocalDate accrualDate, BigDecimal dailyAmountGross) {
      insertAccrualWithFeeMonth(
          fund, feeType, accrualDate, accrualDate.withDayOfMonth(1), dailyAmountGross);
    }

    private void insertAccrualWithFeeMonth(
        TulevaFund fund,
        FeeType feeType,
        LocalDate accrualDate,
        LocalDate feeMonth,
        BigDecimal dailyAmountGross) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_accrual (
                  fund_code, fee_type, accrual_date, fee_month, base_value,
                  annual_rate, daily_amount_gross, days_in_year
              )
              VALUES (
                  :fundCode, :feeType, :accrualDate, :feeMonth, 1000000,
                  0.02, :dailyAmountGross, 365
              )
              """)
          .param("fundCode", fund.name())
          .param("feeType", feeType.name())
          .param("accrualDate", accrualDate)
          .param("feeMonth", feeMonth)
          .param("dailyAmountGross", dailyAmountGross)
          .update();
    }
  }

  @Nested
  class RateSourceRulesHoldForEveryRow {

    @Test
    void everyRateSourceIsAKnownValue() {
      assertThat(violations("rate_source NOT IN ('FIXED', 'TIER')")).isZero();
    }

    @Test
    void onlyTheDepotFeeReadsATier() {
      assertThat(violations("rate_source = 'TIER' AND fee_type <> 'DEPOT'")).isZero();
    }

    @Test
    void aTierRowCarriesNoRateOfItsOwn() {
      assertThat(violations("rate_source = 'TIER' AND annual_rate <> 0")).isZero();
    }

    private Integer violations(String condition) {
      return jdbcClient
          .sql("SELECT COUNT(*) FROM investment_fee_rate WHERE " + condition)
          .query(Integer.class)
          .single();
    }
  }

  @Nested
  class EveryFundAndFeeTypeHasAPolicy {

    @Autowired private FeeChargedToFundPolicy feeChargedToFundPolicy;

    @Test
    void policyResolvesForEveryFundAndFeeTypeOnEveryDateItChangesOn() {
      for (TulevaFund fund : TulevaFund.values()) {
        for (FeeType feeType : FeeType.values()) {
          var resolver = feeChargedToFundPolicy.resolverFor(fund, feeType);
          for (LocalDate date : datesToProbe(fund, feeType)) {
            assertThatCode(() -> resolver.chargedOn(date))
                .as("fee policy for fund=%s, feeType=%s, date=%s", fund, feeType, date)
                .doesNotThrowAnyException();
          }
        }
      }
    }

    private List<LocalDate> datesToProbe(TulevaFund fund, FeeType feeType) {
      var dates = new ArrayList<LocalDate>();
      dates.add(fund.getInceptionDate());
      dates.add(LocalDate.now());
      jdbcClient
          .sql(
              """
              SELECT valid_from, valid_to
              FROM investment_fee_policy
              WHERE fund_code = :fundCode AND fee_type = :feeType
              """)
          .param("fundCode", fund.name())
          .param("feeType", feeType.name())
          .query(
              rs -> {
                dates.add(rs.getDate("valid_from").toLocalDate());
                if (rs.getDate("valid_to") != null) {
                  LocalDate validTo = rs.getDate("valid_to").toLocalDate();
                  dates.add(validTo);
                  dates.add(validTo.plusDays(1));
                }
              });
      return dates;
    }
  }
}
