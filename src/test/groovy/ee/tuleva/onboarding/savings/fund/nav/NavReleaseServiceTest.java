package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.HELD;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.NOT_FINAL;
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
    navReportRepository.markAsPublished(held);

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
  void status_ofAnUnreleasedRevision_namesTheNavTheTrusteeHas() {
    var sent = calculation(TUK75, NAV_DATE, "1.20000", "900000000.00");
    navReportRepository.markAsSent(sent);
    var revision = calculation(TUK75, NAV_DATE, "1.21000", "907500000.00");
    navReportRepository.markAsPublished(revision);

    var status = navReleaseService.status(TUK75, NAV_DATE);

    assertThat(status.calculationId()).isEqualTo(revision);
    assertThat(status.state()).isEqualTo(REVISION);
    assertThat(status.sentNavPerUnit()).isEqualByComparingTo("1.20000");
  }

  @Test
  void status_ofACalculationWhoseEmailNeverWentOut_isNotFinal() {
    var unfinished = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");

    var status = navReleaseService.status(TKF100, NAV_DATE);

    assertThat(status.calculationId()).isEqualTo(unfinished);
    assertThat(status.state()).isEqualTo(NOT_FINAL);
  }

  @Test
  void status_withoutAnyCalculationForTheDate_isNotFound() {
    assertThatThrownBy(() -> navReleaseService.status(TKF100, NAV_DATE))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void release_sendsTheHeldReportToTheTrusteeAndMakesItTheFundsNav() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    var released = navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4066"));

    assertThat(released.state()).isEqualTo(SENT);
    assertThat(navReportRepository.findSentNavPerUnit(NAV_DATE, "TKF100", "NAV"))
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
        .sendMessage(contains("approvedBy=approver-one, approver-two"), eq(SAVINGS));
  }

  @Test
  void release_runsTheNavsChecksAgainOnceTheTrusteeHasIt() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4066"));

    assertThat(events.stream(NavCalculationCompleted.class))
        .containsExactly(new NavCalculationCompleted(List.of(TKF100)));
  }

  @Test
  void release_ofAnOlderNavDate_doesNotRerunTodaysChecks() {
    var olderDate = LocalDate.of(2026, 11, 19);
    var held = calculation(TKF100, olderDate, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(olderDate), eq(NAV_DATE)))
        .willReturn(true);

    navReleaseService.release(TKF100, olderDate, approval(held, "1.4066"));

    assertThat(events.stream(NavCalculationCompleted.class)).isEmpty();
  }

  @Test
  void release_whoseEmailSenderThrows_stampsNothing() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willThrow(new IllegalStateException("Mandrill rejected the recipient"));

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4066")))
        .isInstanceOf(NavReleaseEmailFailedException.class);
    assertThat(navReportRepository.existsSentByNavDateAndFundCode(NAV_DATE, "TKF100")).isFalse();
  }

  @Test
  void release_standsWhenItsSlackMessageFails() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);
    willThrow(new IllegalStateException("Slack webhook failed"))
        .given(notificationService)
        .sendMessage(any(), eq(SAVINGS));

    var released = navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4066"));

    assertThat(released.state()).isEqualTo(SENT);
    assertThat(navReportRepository.findSentCalculationId("TKF100", NAV_DATE)).hasValue(held);
  }

  @Test
  void release_ofARevisionReplacesTheNavTheTrusteeHad() {
    var sent = calculation(TUK75, NAV_DATE, "1.20000", "900000000.00");
    navReportRepository.markAsSent(sent);
    var revision = calculation(TUK75, NAV_DATE, "1.21000", "907500000.00");
    navReportRepository.markAsPublished(revision);
    given(navReportEmailSender.send(anyList(), eq(TUK75), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(true);

    navReleaseService.release(
        TUK75,
        NAV_DATE,
        new NavReleaseApproval(
            revision, new BigDecimal("1.21000"), new BigDecimal("907500000.00"), APPROVERS));

    assertThat(navReportRepository.findSentCalculationId("TUK75", NAV_DATE)).hasValue(revision);
  }

  @Test
  void release_stampsNothingWhenTheEmailToTheTrusteeFails() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);
    given(navReportEmailSender.send(anyList(), eq(TKF100), eq(NAV_DATE), eq(CALCULATION_DATE)))
        .willReturn(false);

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4066")))
        .isInstanceOf(NavReleaseEmailFailedException.class);

    assertThat(navReportRepository.existsSentByNavDateAndFundCode(NAV_DATE, "TKF100")).isFalse();
    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void release_refusesACalculationThatIsNoLongerTheDatesLatest() {
    var older = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(older);
    calculation(TKF100, NAV_DATE, "1.4100", "16040000.00");

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval(older, "1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_refusesValuesOtherThanTheCalculations() {
    var held = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsPublished(held);

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval(held, "1.4067")))
        .isInstanceOf(NavReleaseRefusedException.class);
    assertThatThrownBy(
            () ->
                navReleaseService.release(
                    TKF100,
                    NAV_DATE,
                    new NavReleaseApproval(
                        held, new BigDecimal("1.4066"), new BigDecimal("16000000.01"), APPROVERS)))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_refusesACalculationTheTrusteeAlreadyHas() {
    var sent = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");
    navReportRepository.markAsSent(sent);

    assertThatThrownBy(() -> navReleaseService.release(TKF100, NAV_DATE, approval(sent, "1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_refusesACalculationWhoseEmailNeverWentOut() {
    var unfinished = calculation(TKF100, NAV_DATE, "1.4066", "16000000.00");

    assertThatThrownBy(
            () -> navReleaseService.release(TKF100, NAV_DATE, approval(unfinished, "1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_refusesACalculationOfAnotherFundOrDate() {
    var otherDate = calculation(TKF100, NAV_DATE.minusDays(1), "1.4066", "16000000.00");
    navReportRepository.markAsPublished(otherDate);

    assertThatThrownBy(
            () -> navReleaseService.release(TKF100, NAV_DATE, approval(otherDate, "1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    assertThatThrownBy(
            () ->
                navReleaseService.release(
                    TUK75, NAV_DATE.minusDays(1), approval(otherDate, "1.4066")))
        .isInstanceOf(NavReleaseRefusedException.class);
    then(navReportEmailSender).shouldHaveNoInteractions();
  }

  @Test
  void release_ofAnUnknownCalculation_isNotFound() {
    assertThatThrownBy(
            () ->
                navReleaseService.release(TKF100, NAV_DATE, approval(UUID.randomUUID(), "1.4066")))
        .isInstanceOf(NoSuchElementException.class);
  }

  private static NavReleaseApproval approval(UUID calculationId, String navPerUnit) {
    return new NavReleaseApproval(
        calculationId, new BigDecimal(navPerUnit), new BigDecimal("16000000.00"), APPROVERS);
  }

  private UUID calculation(
      ee.tuleva.onboarding.tulevafund.TulevaFund fund,
      LocalDate navDate,
      String navPerUnit,
      String aum) {
    var calculationId = UUID.randomUUID();
    navReportRepository.saveAll(
        List.of(
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
                .build()));
    return calculationId;
  }
}
