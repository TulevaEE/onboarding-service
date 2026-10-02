package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NavNotDueYet;
import ee.tuleva.onboarding.investment.check.tracking.FundCheck.NotCheckable;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissingNavClassifierTest {

  private static final LocalDate DAY_BEFORE_CHRISTMAS_EVE = LocalDate.of(2026, 12, 23);
  private static final LocalDate ITS_WORKING_DAY_BEFORE = LocalDate.of(2026, 12, 22);

  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock FundNavQueryService fundNavQueryService;

  @BeforeEach
  void aFundWithAModelAndAnEarlierNav() {
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, DAY_BEFORE_CHRISTMAS_EVE))
        .willReturn(
            List.of(
                ModelPortfolioAllocation.builder()
                    .fund(TUK75)
                    .effectiveDate(LocalDate.of(2026, 1, 1))
                    .isin("ZZ0000000001")
                    .weight(BigDecimal.ONE)
                    .build()));
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUK75.getCode(), ITS_WORKING_DAY_BEFORE))
        .willReturn(Optional.of(ITS_WORKING_DAY_BEFORE));
  }

  @Test
  void aNavMissingOnTheHolidayEveningsBeforeItIsCalculatedIsNotDueYet() {
    var christmasEveEvening = classifierAt("2026-12-24T17:00:00Z");

    assertThat(
            christmasEveEvening.classify(
                TUK75, DAY_BEFORE_CHRISTMAS_EVE, ITS_WORKING_DAY_BEFORE, true))
        .isEqualTo(new NavNotDueYet());
  }

  @Test
  void aNavStillMissingOnTheWorkingDayItIsCalculatedIsNamed() {
    var nextWorkingDayEvening = classifierAt("2026-12-28T17:00:00Z");

    assertThat(
            nextWorkingDayEvening.classify(
                TUK75, DAY_BEFORE_CHRISTMAS_EVE, ITS_WORKING_DAY_BEFORE, true))
        .isEqualTo(new NotCheckable("no NAV for the check date"));
  }

  private MissingNavClassifier classifierAt(String instant) {
    return new MissingNavClassifier(
        Clock.fixed(Instant.parse(instant), ZoneId.of("Europe/Tallinn")),
        modelPortfolioAllocationRepository,
        fundNavQueryService,
        new PublicHolidays());
  }
}
