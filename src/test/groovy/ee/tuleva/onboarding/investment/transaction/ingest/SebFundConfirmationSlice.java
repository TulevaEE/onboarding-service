package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.time.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({
  ClockConfig.class,
  InvestmentReportService.class,
  CsvToJsonConverter.class,
  SebPendingTransactionExtractor.class,
  SebClientNameToFundResolver.class,
  Target2Calendar.class,
  PositionPriceResolver.class,
  PriorityPriceProvider.class,
  JdbcFundValueRepository.class
})
@ComponentScan(
    basePackageClasses = InstrumentReferenceService.class,
    useDefaultFilters = false,
    includeFilters =
        @ComponentScan.Filter(
            type = REGEX,
            pattern =
                "ee\\.tuleva\\.onboarding\\.instrument\\.(InstrumentReferenceService"
                    + "|InstrumentSnapshotLoader|BenchmarkCategoryProxyRepository"
                    + "|BenchmarkProxyPeriodRepository)"))
class SebFundConfirmationSlice {}
