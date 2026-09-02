package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavRevisionServiceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 9, 1);
  private static final LocalDate NEXT_WORKING_DAY = LocalDate.of(2026, 9, 2);
  private static final BigDecimal PUBLISHED_NAV = new BigDecimal("1.60377");

  @Mock private NavReportRepository navReportRepository;
  @Mock private FundNavQueryService fundNavQueryService;
  @Mock private NavCalculationService navCalculationService;
  @Mock private NavPublisher navPublisher;
  @Mock private OperationsNotificationService notificationService;
  @Spy private PublicHolidays publicHolidays = new PublicHolidays();

  @InjectMocks private NavRevisionService service;

  @Test
  void onNavPositionsUpdated_publishesRevision_whenNavAlreadyPublished() {
    given(navReportRepository.existsPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(true);
    given(fundNavQueryService.findPublishedNavPerUnit("TUK75", NAV_DATE))
        .willReturn(Optional.of(PUBLISHED_NAV));
    var revised = revisedResult();
    given(navCalculationService.calculate(TUK75, NEXT_WORKING_DAY)).willReturn(revised);

    service.onNavPositionsUpdated(new NavPositionsUpdated(TUK75, NAV_DATE, 4));

    verify(navPublisher).publishRevision(revised, new NavRevision(PUBLISHED_NAV, 4));
    verifyNoInteractions(notificationService);
  }

  @Test
  void onNavPositionsUpdated_doesNothing_whenNavNotPublishedYet() {
    given(navReportRepository.existsPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(false);

    service.onNavPositionsUpdated(new NavPositionsUpdated(TUK75, NAV_DATE, 4));

    verifyNoInteractions(navCalculationService, navPublisher, notificationService);
  }

  @Test
  void onNavPositionsUpdated_alertsInsteadOfThrowing_whenRevisionFails() {
    given(navReportRepository.existsPublishedByNavDateAndFundCode(NAV_DATE, "TUK75"))
        .willReturn(true);
    given(fundNavQueryService.findPublishedNavPerUnit("TUK75", NAV_DATE))
        .willReturn(Optional.of(PUBLISHED_NAV));
    given(navCalculationService.calculate(TUK75, NEXT_WORKING_DAY))
        .willThrow(new IllegalStateException("No position report found"));

    service.onNavPositionsUpdated(new NavPositionsUpdated(TUK75, NAV_DATE, 4));

    verify(navPublisher, never()).publishRevision(any(), any());
    verify(notificationService)
        .sendMessage(contains("fund=TUK75, navDate=2026-09-01"), eq(SAVINGS));
  }

  private static NavCalculationResult revisedResult() {
    return NavCalculationResult.builder()
        .fund(TUK75)
        .calculationDate(NEXT_WORKING_DAY)
        .securitiesValue(new BigDecimal("1124014464.12"))
        .cashPosition(new BigDecimal("12900827.85"))
        .receivables(BigDecimal.ZERO)
        .pendingSubscriptions(BigDecimal.ZERO)
        .pendingRedemptions(BigDecimal.ZERO)
        .managementFeeAccrual(new BigDecimal("6418.71"))
        .depotFeeAccrual(BigDecimal.ZERO)
        .payables(BigDecimal.ZERO)
        .blackrockAdjustment(BigDecimal.ZERO)
        .aum(new BigDecimal("1136908873.26"))
        .unitsOutstanding(new BigDecimal("712594777.749"))
        .navPerUnit(new BigDecimal("1.59545"))
        .positionReportDate(NAV_DATE)
        .priceDate(NAV_DATE)
        .calculatedAt(Instant.parse("2026-09-02T09:05:00Z"))
        .securitiesDetail(List.of())
        .build();
  }
}
