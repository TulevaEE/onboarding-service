package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_LEFT_UNEXECUTED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBriefJob.BRIEF_CRON;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BriefedBatches {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Comparator<OutgoingPayment> IN_THE_ORDER_SENT =
      comparing(OutgoingPayment::getAttemptedAt).thenComparing(OutgoingPayment::getEndToEndId);

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final PaymentCheckService paymentCheckService;
  private final PublicHolidays publicHolidays;

  BriefedBatch on(LocalDate today) {
    var after = briefOn(publicHolidays.previousWorkingDay(today));
    var until = briefOn(today);
    var payments =
        outgoingPaymentRepository.findByAttemptedAtBetween(after, until).stream()
            .filter(payment -> payment.getAttemptedAt().isAfter(after))
            .sorted(IN_THE_ORDER_SENT)
            .toList();
    return new BriefedBatch(after, until, payments, fromEarlierBriefs(after, until));
  }

  private List<OutgoingPayment> fromEarlierBriefs(Instant after, Instant until) {
    var stillAwaitingApproval =
        outgoingPaymentRepository.findAwaitingApproval().stream()
            .filter(payment -> !payment.getAttemptedAt().isAfter(after))
            .filter(payment -> !alreadyReportedLeftUnexecuted(payment));
    var resolvedSinceTodaysBrief =
        outgoingPaymentRepository
            .findByAttemptedAtLessThanEqualAndResolvedAtAfter(after, until)
            .stream()
            .filter(payment -> !payment.isAwaitingApproval());
    return Stream.concat(stillAwaitingApproval, resolvedSinceTodaysBrief)
        .sorted(IN_THE_ORDER_SENT)
        .toList();
  }

  private boolean alreadyReportedLeftUnexecuted(OutgoingPayment payment) {
    return paymentCheckService.hasRecorded(PAYMENT_LEFT_UNEXECUTED, payment.submissionKey());
  }

  private static Instant briefOn(LocalDate day) {
    return requireNonNull(CronExpression.parse(BRIEF_CRON).next(day.atStartOfDay(TALLINN)))
        .toInstant();
  }
}
