package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBriefJob.BRIEF_CRON;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BriefedBatches {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final PublicHolidays publicHolidays;

  BriefedBatch on(LocalDate today) {
    var after = briefOn(publicHolidays.previousWorkingDay(today));
    var until = briefOn(today);
    var payments =
        outgoingPaymentRepository.findByAttemptedAtBetween(after, until).stream()
            .filter(payment -> payment.getAttemptedAt().isAfter(after))
            .toList();
    var carriedOver =
        outgoingPaymentRepository.findAwaitingApproval().stream()
            .filter(payment -> !payment.getAttemptedAt().isAfter(after))
            .toList();
    return new BriefedBatch(after, until, payments, carriedOver);
  }

  private static Instant briefOn(LocalDate day) {
    return requireNonNull(CronExpression.parse(BRIEF_CRON).next(day.atStartOfDay(TALLINN)))
        .toInstant();
  }
}
