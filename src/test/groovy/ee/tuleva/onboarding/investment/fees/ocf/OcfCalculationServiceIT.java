package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeRateSource.FIXED;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.DepotRateResolver;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({
  OcfCalculationServiceIT.Json.class,
  OcfCalculationService.class,
  OcfSnapshotRepository.class,
  OcfJson.class,
  MonthEndNavCheck.class,
  FundNavQueryService.class,
  PublicHolidays.class
})
class OcfCalculationServiceIT {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate TUESDAY_SEPTEMBER_29 = LocalDate.of(2026, 9, 29);
  private static final LocalDate WEDNESDAY_SEPTEMBER_30 = LocalDate.of(2026, 9, 30);
  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final LocalDate FRIDAY_MAY_29 = LocalDate.of(2026, 5, 29);
  private static final String PUBLISHED_IN = "KID 2026";

  @TestConfiguration
  static class Json {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @MockitoBean private FeeRateRepository feeRateRepository;
  @MockitoBean private FeeChargedToFundPolicy feeChargedToFundPolicy;
  @MockitoBean private DepotRateResolver depotRateResolver;
  @MockitoBean private InstrumentOcfService instrumentOcfService;
  @MockitoBean private OcfNotifier ocfNotifier;

  @Autowired private OcfCalculationService service;
  @Autowired private OcfSnapshotRepository snapshotRepository;
  @Autowired private NavReportRepository navReportRepository;

  @BeforeEach
  void aManagementFeeRateSoTheNavIsTheOnlyInputThatCanLeaveAGap() {
    given(feeRateRepository.findValidRate(any(), any(), any()))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L,
                    TUK75,
                    MANAGEMENT,
                    new BigDecimal("0.0020"),
                    FIXED,
                    LocalDate.of(2026, 1, 1),
                    null)));
  }

  @Test
  void aMonthEndNavNotYetPublishedStoresTheSnapshotIncompleteAndPublishingRefusesIt() {
    publishedNav(TUESDAY_SEPTEMBER_29);
    unpublishedNav(WEDNESDAY_SEPTEMBER_30);

    service.calculateOcf(TUK75, SEPTEMBER);

    var stored = storedSnapshot(SEPTEMBER);
    assertThat(stored.complete()).isFalse();
    assertThat(stored.checks()).contains("\"gaps\":[\"MONTH_END_NAV_NOT_PUBLISHED\"]");
    assertThat(stored.audit().navDate()).isEqualTo(TUESDAY_SEPTEMBER_29);
    assertThatThrownBy(() -> service.publish(TUK75, SEPTEMBER, PUBLISHED_IN))
        .isInstanceOf(IncompleteOcfSnapshotException.class);
  }

  @Test
  void publishingTheMonthEndNavAndRerunningClearsTheGapSoTheSnapshotPublishes() {
    publishedNav(TUESDAY_SEPTEMBER_29);
    var monthEndCalculation = unpublishedNav(WEDNESDAY_SEPTEMBER_30);
    service.calculateOcf(TUK75, SEPTEMBER);
    navReportRepository.markAsPublished(monthEndCalculation);

    service.calculateOcf(TUK75, SEPTEMBER);

    var stored = storedSnapshot(SEPTEMBER);
    assertThat(stored.complete()).isTrue();
    assertThat(stored.checks()).contains("\"gaps\":[]");
    assertThat(stored.audit().navDate()).isEqualTo(WEDNESDAY_SEPTEMBER_30);
    assertThat(service.publish(TUK75, SEPTEMBER, PUBLISHED_IN)).isTrue();
  }

  @Test
  void aMonthEndingOnAWeekendIsCompleteOnTheNavOfItsLastWorkingDay() {
    publishedNav(FRIDAY_MAY_29);

    service.calculateOcf(TUK75, MAY);

    var stored = storedSnapshot(MAY);
    assertThat(stored.complete()).isTrue();
    assertThat(stored.checks()).contains("\"gaps\":[]");
    assertThat(stored.audit().navDate()).isEqualTo(FRIDAY_MAY_29);
  }

  private void publishedNav(LocalDate navDate) {
    nav(navDate, navDate.atTime(16, 0).toInstant(UTC));
  }

  private UUID unpublishedNav(LocalDate navDate) {
    return nav(navDate, null);
  }

  private UUID nav(LocalDate navDate, @Nullable Instant publishedAt) {
    var calculationId = UUID.randomUUID();
    navReportRepository.saveAllAndFlush(
        List.of(
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(TUK75.getCode())
                .accountType("UNITS")
                .accountName("Synthetic units")
                .marketValue(new BigDecimal("1000000.00"))
                .calculationId(calculationId)
                .publishedAt(publishedAt)
                .build()));
    return calculationId;
  }

  private OcfSnapshot storedSnapshot(YearMonth month) {
    return snapshotRepository.findByFundAndMonth(TUK75.getCode(), month.atDay(1)).orElseThrow();
  }
}
