package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.HELD;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.REVISION;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.SENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest
@Import({
  NavReleaseService.class,
  NavCalendar.class,
  PublicHolidays.class,
  NavReleaseServiceTest.OnTheWorkingDayAfterTheNavDate.class
})
@RecordApplicationEvents
class NavReleaseServiceTest {

  @TestConfiguration
  static class OnTheWorkingDayAfterTheNavDate {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 20);
  private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 11, 23);
  private static final List<String> APPROVERS = List.of("approver-one", "approver-two");

  @Autowired private NavReportRepository navReportRepository;
  @Autowired private NavReleaseService navReleaseService;
  @Autowired private ApplicationEvents events;

  @MockitoBean private NavReportEmailSender navReportEmailSender;
  @MockitoBean private OperationsNotificationService notificationService;

  @Test
  void status_ofANavHeldForReview_isHeldWithTheValuesToApprove() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");

    assertThat(navReleaseService.status(TKF100, NAV_DATE))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new NavReleaseStatus(
                "TKF100",
                NAV_DATE,
                held,
                HELD,
                new BigDecimal("1.4066"),
                new BigDecimal("16000000.00"),
                null));
  }

  @Test
  void status_ofAnUnreleasedRevision_namesTheNavSebHas() {
    var sent = calculation(TUK75, NAV_DATE, "1.20000", "900000000.00");
    navReportRepository.markAsPublished(sent);
    var revision = calculation(TUK75, NAV_DATE, "1.21000", "907500000.00");

    var status = navReleaseService.status(TUK75, NAV_DATE);

    assertThat(status.calculationId()).isEqualTo(revision);
    assertThat(status.state()).isEqualTo(REVISION);
    assertThat(status.sentNavPerUnit()).isEqualByComparingTo("1.20000");
  }

  @Test
  void status_ofANavSebHas_isSent() {
    var sent = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(sent);

    var status = navReleaseService.status(TKF100, NAV_DATE);

    assertThat(status.calculationId()).isEqualTo(sent);
    assertThat(status.state()).isEqualTo(SENT);
  }

  @Test
  void status_withoutAnyCalculationForTheDate_isNotFound() {
    assertThatThrownBy(() -> navReleaseService.status(TKF100, NAV_DATE))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void release_sendsTheHeldReportToTheTrusteeAndPublishesIt() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    var released = navReleaseService.release(TKF100, NAV_DATE, approval("1.4066"));

    assertThat(released.state()).isEqualTo(SENT);
    assertThat(released.calculationId()).isEqualTo(held);
    assertThat(navReportRepository.findPublishedNavPerUnit(NAV_DATE, "TKF100", "NAV"))
        .get(as(BIG_DECIMAL))
        .isEqualByComparingTo("1.4066");
    then(navReportEmailSender)
        .should()
        .send(
            argThat(
                rows ->
                    rows.size() == 2
                        && rows.stream().allMatch(row -> held.equals(row.getCalculationId()))),
            eq(TKF100),
            eq(NAV_DATE),
            eq(CALCULATION_DATE));
    then(notificationService)
        .should()
        .sendMessage(
            argThat(
                message ->
                    message.contains("approved by 2 people")
                        && !message.contains("approver-one")
                        && !message.contains("approver-two")),
            eq(SAVINGS));
  }

  @Test
  void release_sendsTheRecalculationThatReplacedTheApprovedOne_whenItHasTheApprovedValues() {
    calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    var recalculated = UUID.randomUUID();
    navReportRepository.replaceByNavDateAndFundCode(
        NAV_DATE, "TKF100", rows(TKF100, NAV_DATE, "1.4066", "16000000.00", recalculated));
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    var released = navReleaseService.release(TKF100, NAV_DATE, approval("1.4066"));

    assertThat(released.calculationId()).isEqualTo(recalculated);
    assertThat(navReportRepository.findAll())
        .allSatisfy(row -> assertThat(row.getCalculationId()).isEqualTo(recalculated))
        .allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
  }

  @Test
  void release_runsTheNavsChecksAgainOnceTheTrusteeHasIt() {
    calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    navReleaseService.release(TKF100, NAV_DATE, approval("1.4066"));

    assertThat(events.stream(NavCalculationCompleted.class))
        .containsExactly(new NavCalculationCompleted(List.of(TKF100)));
  }

  @Test
  void release_ofAnOlderNavDate_doesNotRerunTodaysChecks() {
    var olderDate = LocalDate.of(2026, 11, 19);
    calculation(TKF100, olderDate, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(olderDate), eq(NAV_DATE)))
        .willReturn(true);

    navReleaseService.release(TKF100, olderDate, approval("1.4066"));

    assertThat(events.stream(NavCalculationCompleted.class)).isEmpty();
  }

  @Test
  void release_whoseEmailSenderThrows_publishesNothing() {
    calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willThrow(new IllegalStateException("Mandrill rejected the recipient"));

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval("1.4066")))
        .isInstanceOf(NavReleaseEmailFailedException.class);
    assertThat(navReportRepository.existsPublishedByNavDateAndFundCode(NAV_DATE, "TKF100"))
        .isFalse();
  }

  @Test
  void release_standsWhenItsSlackMessageFails() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);
    willThrow(new IllegalStateException("Slack webhook failed"))
        .given(notificationService)
        .sendMessage(any(), eq(SAVINGS));

    var released = navReleaseService.release(TKF100, NAV_DATE, approval("1.4066"));

    assertThat(released.state()).isEqualTo(SENT);
    assertThat(navReportRepository.findByFundCodeAndNavDateOrderById("TKF100", NAV_DATE))
        .allSatisfy(row -> assertThat(row.getCalculationId()).isEqualTo(held))
        .allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
  }

  @Test
  void release_ofARevisionReplacesTheNavSebHad() {
    var sent = calculation(TUK75, NAV_DATE, "1.20000", "900000000.00");
    navReportRepository.markAsPublished(sent);
    calculation(TUK75, NAV_DATE, "1.21000", "907500000.00");
    given(navReportEmailSender.send(anyList(), eq(TUK75), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    navReleaseService.release(
        TUK75,
        NAV_DATE,
        new NavReleaseApproval(
            new BigDecimal("1.21000"), new BigDecimal("907500000.00"), APPROVERS));

    assertThat(navReportRepository.findPublishedNavPerUnit(NAV_DATE, "TUK75", "NAV"))
        .get(as(BIG_DECIMAL))
        .isEqualByComparingTo("1.21000");
    then(notificationService)
        .should()
        .sendMessage(contains("replaces the NAV SEB had (1.20000"), eq(SAVINGS));
  }

  @Test
  void release_publishesNothingWhenTheEmailToTheTrusteeFails() {
    calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(false);

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval("1.4066")))
        .isInstanceOf(NavReleaseEmailFailedException.class);

    assertThat(navReportRepository.existsPublishedByNavDateAndFundCode(NAV_DATE, "TKF100"))
        .isFalse();
    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void release_refusesValuesOtherThanTheDatesLatestCalculations() {
    calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    calculation(TKF100, NAV_DATE, "1.4100", "16040000.00");

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval("1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    assertThatThrownBy(
            () ->
                navReleaseService.release(
                    TKF100,
                    NAV_DATE,
                    new NavReleaseApproval(
                        new BigDecimal("1.4100"), new BigDecimal("16040000.01"), APPROVERS)))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_refusesANavSebAlreadyHas() {
    var sent = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(sent);

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval("1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_ofAnotherFundOrDate_isNotFound() {
    calculation(TKF100, NAV_DATE.minusDays(1), "1.4066", "16000000.00");

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval("1.4066")))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(
            () -> navReleaseService.release(TUK75, NAV_DATE.minusDays(1), approval("1.4066")))
        .isInstanceOf(NoSuchElementException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  private static NavReleaseApproval approval(String navPerUnit) {
    return new NavReleaseApproval(
        new BigDecimal(navPerUnit), new BigDecimal("16000000.00"), APPROVERS);
  }

  private UUID calculation(
      ee.tuleva.onboarding.tulevafund.TulevaFund fund,
      LocalDate navDate,
      String navPerUnit,
      String aum) {
    var calculationId = UUID.randomUUID();
    navReportRepository.saveAll(rows(fund, navDate, navPerUnit, aum, calculationId));
    return calculationId;
  }

  private static List<NavReportRow> rows(
      ee.tuleva.onboarding.tulevafund.TulevaFund fund,
      LocalDate navDate,
      String navPerUnit,
      String aum,
      UUID calculationId) {
    return List.of(
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(fund.getCode())
            .accountType("UNITS")
            .accountName("Total outstanding units:")
            .quantity(new BigDecimal("11374000.000"))
            .marketPrice(new BigDecimal(navPerUnit))
            .marketValue(new BigDecimal(aum))
            .calculationId(calculationId)
            .build(),
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(fund.getCode())
            .accountType("NAV")
            .accountName("Net Asset Value")
            .quantity(BigDecimal.ONE)
            .marketPrice(new BigDecimal(navPerUnit))
            .marketValue(new BigDecimal(navPerUnit))
            .calculationId(calculationId)
            .build());
  }
}
