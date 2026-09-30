package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({
  InstrumentRateCoverageCheckerIT.RateService.class,
  InstrumentRateCoverageChecker.class,
  FundNavQueryService.class
})
class InstrumentRateCoverageCheckerIT {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 9, 28);
  private static final String HELD = "ZZ0000000001";
  private static final String MODELLED_ONLY = "ZZ0000000002";

  @TestConfiguration
  @ComponentScan(
      basePackageClasses = InstrumentOcfService.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern =
                  "ee\\.tuleva\\.onboarding\\.investment\\.fees\\.rate\\.(InstrumentOcfService"
                      + "|InstrumentFeeAgreementRepository|InstrumentFeeRateRepository"
                      + "|MonthVolumeReader|RebateCalculator)"))
  static class RateService {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @MockitoBean private FundValueProvider fundValueProvider;

  @Autowired private InstrumentRateCoverageChecker checker;
  @Autowired private NavReportRepository navReportRepository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void aHeldInstrumentWithNoAgreementFailsBecauseTheMonthsOcfCannotBeCalculated() {
    held(HELD);

    assertThat(checker.check(TUK75, CHECK_DATE))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(tuple(FAIL, List.of(HELD)));
  }

  @Test
  void aModelInstrumentNotYetHeldWithNoAgreementIsAWarning() {
    held(HELD);
    agreementFor(HELD);
    modelled(MODELLED_ONLY);

    assertThat(checker.check(TUK75, CHECK_DATE))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(tuple(WARNING, List.of(MODELLED_ONLY)));
  }

  @Test
  void aFundWithNoPublishedNavCannotBeCheckedAndSaysSoRatherThanPassing() {
    assertThat(checker.check(TUK75, CHECK_DATE))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(tuple(NOT_RUN, List.of()));
  }

  @Test
  void enteringTheMissingAgreementClearsTheFinding() {
    held(HELD);
    agreementFor(HELD);

    assertThat(checker.check(TUK75, CHECK_DATE))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(PASS);
  }

  private void held(String isin) {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(CHECK_DATE)
            .fundCode(TUK75.getCode())
            .accountType("SECURITY")
            .accountName("Synthetic holding")
            .accountId(isin)
            .marketValue(new BigDecimal("1000000.00"))
            .calculationId(UUID.randomUUID())
            .publishedAt(CHECK_DATE.atTime(12, 0).toInstant(UTC))
            .build());
  }

  private void agreementFor(String isin) {
    inTheInstrumentReference(isin);
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from)
            VALUES (:isin, 0.00070000, 'NONE', '{}', DATE '2026-01-01')
            """)
        .param("isin", isin)
        .update();
  }

  private void inTheInstrumentReference(String isin) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", isin)
        .update();
  }

  private void modelled(String isin) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_model_portfolio_allocation (fund_code, effective_date, isin, weight)
            VALUES ('TUK75', DATE '2026-09-01', :isin, 0.25)
            """)
        .param("isin", isin)
        .update();
  }
}
