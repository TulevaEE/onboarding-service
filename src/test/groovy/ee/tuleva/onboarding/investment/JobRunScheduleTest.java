package ee.tuleva.onboarding.investment;

import static ee.tuleva.onboarding.investment.JobRunScheduleTest.ImportedDataUse.RUNS_THE_IMPORT;
import static ee.tuleva.onboarding.investment.JobRunScheduleTest.ImportedDataUse.STORES_A_RESULT_DERIVED_FROM_THE_IMPORT;
import static ee.tuleva.onboarding.investment.JobRunScheduleTest.ImportedDataUse.STORES_NOTHING_DERIVED_FROM_THE_IMPORT;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.ReportImportJob;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.support.CronExpression;

class JobRunScheduleTest {

  private static final ZoneId TALLINN = ZoneId.of(JobRunSchedule.TIMEZONE);

  private static final Duration REPORT_IMPORT_LOCK_AT_MOST = reportImportLockAtMost();

  enum ImportedDataUse {
    RUNS_THE_IMPORT,
    STORES_A_RESULT_DERIVED_FROM_THE_IMPORT,
    STORES_NOTHING_DERIVED_FROM_THE_IMPORT
  }

  private enum ScheduledSlot {
    IMPORT_BUSINESS_HOURS(JobRunSchedule.IMPORT_BUSINESS_HOURS, RUNS_THE_IMPORT),
    TRANSACTION_COMMAND(JobRunSchedule.TRANSACTION_COMMAND, STORES_NOTHING_DERIVED_FROM_THE_IMPORT),
    TRACKING_DIFFERENCE_GAP_FILL(
        JobRunSchedule.TRACKING_DIFFERENCE_GAP_FILL, STORES_A_RESULT_DERIVED_FROM_THE_IMPORT),
    FEE_ACCRUAL_POSITION_BACKFILL(
        JobRunSchedule.FEE_ACCRUAL_POSITION_BACKFILL, STORES_A_RESULT_DERIVED_FROM_THE_IMPORT),
    LIMIT_CHECK_BACKFILL(
        JobRunSchedule.LIMIT_CHECK_BACKFILL, STORES_A_RESULT_DERIVED_FROM_THE_IMPORT),
    PEVA_RAVA_PHASE_UPDATE(
        JobRunSchedule.PEVA_RAVA_PHASE_UPDATE, STORES_NOTHING_DERIVED_FROM_THE_IMPORT),
    PEVA_RAVA_FLOW_RECALC(
        JobRunSchedule.PEVA_RAVA_FLOW_RECALC, STORES_NOTHING_DERIVED_FROM_THE_IMPORT),
    R16_FLOW_RECALC(JobRunSchedule.R16_FLOW_RECALC, STORES_NOTHING_DERIVED_FROM_THE_IMPORT),
    RISK_INDICATOR_DAILY(
        JobRunSchedule.RISK_INDICATOR_DAILY, STORES_NOTHING_DERIVED_FROM_THE_IMPORT),
    JOB_TRIGGER_POLL(JobRunSchedule.JOB_TRIGGER_POLL, STORES_NOTHING_DERIVED_FROM_THE_IMPORT);

    private final String cron;
    private final ImportedDataUse importedDataUse;

    ScheduledSlot(String cron, ImportedDataUse importedDataUse) {
      this.cron = cron;
      this.importedDataUse = importedDataUse;
    }

    boolean mustStayClearOfTheImportLockWindow() {
      return importedDataUse == STORES_A_RESULT_DERIVED_FROM_THE_IMPORT;
    }
  }

  @Test
  void everyScheduleConstantIsClassifiedByWhatItDoesWithImportedData() {
    Map<String, String> classified =
        stream(ScheduledSlot.values()).collect(toMap(Enum::name, slot -> slot.cron));

    assertThat(declaredCronConstants()).isEqualTo(classified);
  }

  @Test
  void slotsStoringResultsDerivedFromTheImportNeverFireWhileAnImportCanHoldItsLock() {
    ZonedDateTime yearStart = LocalDateTime.parse("2026-01-01T00:00:00").atZone(TALLINN);
    ZonedDateTime yearEnd = yearStart.plusYears(1);

    assertThat(
            stream(ScheduledSlot.values())
                .filter(ScheduledSlot::mustStayClearOfTheImportLockWindow))
        .isNotEmpty()
        .allSatisfy(
            slot ->
                assertThat(firesBetween(slot.cron, yearStart, yearEnd))
                    .isNotEmpty()
                    .allSatisfy(
                        fire ->
                            assertThat(importLockWindow(fire.toLocalDate()).covers(fire))
                                .as("slot=%s, cron=%s, fire=%s", slot, slot.cron, fire)
                                .isFalse()));
  }

  @Test
  void theImportLockWindowRunsFromTheFirstImportUntilTheLastImportsLockExpires() {
    LocalDate day = LocalDate.parse("2026-04-13");
    ImportLockWindow window = importLockWindow(day);

    assertThat(window.from()).isEqualTo(at(day, "08:00"));
    assertThat(window.until()).isEqualTo(at(day, "18:50"));
    assertThat(window.covers(at(day, "07:59"))).isFalse();
    assertThat(window.covers(at(day, "08:00"))).isTrue();
    assertThat(window.covers(at(day, "18:30"))).isTrue();
    assertThat(window.covers(at(day, "18:50"))).isTrue();
    assertThat(window.covers(at(day, "18:51"))).isFalse();
  }

  @Test
  void importBusinessHoursFiresEveryFiveMinutesFromEightThroughSeventeen() {
    ZonedDateTime dayStart = LocalDateTime.parse("2026-04-13T00:00:00").atZone(TALLINN);
    List<ZonedDateTime> fires =
        firesBetween(JobRunSchedule.IMPORT_BUSINESS_HOURS, dayStart, dayStart.plusDays(1));

    assertThat(fires).hasSize(120);
    assertThat(fires.getFirst()).isEqualTo(at(dayStart.toLocalDate(), "08:00"));
    assertThat(fires.getLast()).isEqualTo(at(dayStart.toLocalDate(), "17:55"));
    assertThat(intervalsBetween(fires)).containsOnly(Duration.ofMinutes(5));
  }

  @Test
  void trackingDifferenceGapFillFiresOncePerBusinessDay() {
    ZonedDateTime weekStart = LocalDateTime.parse("2026-04-13T00:00:00").atZone(TALLINN);
    List<ZonedDateTime> fires =
        firesBetween(JobRunSchedule.TRACKING_DIFFERENCE_GAP_FILL, weekStart, weekStart.plusDays(7));

    assertThat(fires).hasSize(5);
    assertThat(fires)
        .allSatisfy(fire -> assertThat(fire.toLocalTime()).isEqualTo(LocalTime.of(19, 0)));
    assertThat(fires)
        .allSatisfy(fire -> assertThat(fire.getDayOfWeek().getValue()).isLessThanOrEqualTo(5));
  }

  @Test
  void importBusinessHoursCoverTheIntradayCorrectionTheOldScheduleMissed() {
    ZonedDateTime correctionArrived = LocalDateTime.parse("2026-04-10T13:29:00").atZone(TALLINN);
    ZonedDateTime nextFire =
        CronExpression.parse(JobRunSchedule.IMPORT_BUSINESS_HOURS).next(correctionArrived);

    assertThat(nextFire).isNotNull();
    assertThat(nextFire).isBefore(LocalDateTime.parse("2026-04-10T16:00:00").atZone(TALLINN));
  }

  private record ImportLockWindow(ZonedDateTime from, ZonedDateTime until) {

    boolean covers(ZonedDateTime moment) {
      return !moment.isBefore(from) && !moment.isAfter(until);
    }
  }

  private static ImportLockWindow importLockWindow(LocalDate date) {
    ZonedDateTime dayStart = date.atStartOfDay(TALLINN);
    List<ZonedDateTime> imports =
        firesBetween(JobRunSchedule.IMPORT_BUSINESS_HOURS, dayStart, dayStart.plusDays(1));
    return new ImportLockWindow(
        imports.getFirst(), imports.getLast().plus(REPORT_IMPORT_LOCK_AT_MOST));
  }

  private static List<ZonedDateTime> firesBetween(
      String cron, ZonedDateTime from, ZonedDateTime to) {
    CronExpression expression = CronExpression.parse(cron);
    return Stream.iterate(expression.next(from), Objects::nonNull, expression::next)
        .takeWhile(to::isAfter)
        .toList();
  }

  private static List<Duration> intervalsBetween(List<ZonedDateTime> fires) {
    return Stream.iterate(1, index -> index < fires.size(), index -> index + 1)
        .map(index -> Duration.between(fires.get(index - 1), fires.get(index)))
        .distinct()
        .toList();
  }

  private static ZonedDateTime at(LocalDate date, String time) {
    return date.atTime(LocalTime.parse(time)).atZone(TALLINN);
  }

  private static Map<String, String> declaredCronConstants() {
    return stream(JobRunSchedule.class.getDeclaredFields())
        .filter(field -> field.getType() == String.class)
        .filter(field -> isPublic(field.getModifiers()) && isStatic(field.getModifiers()))
        .filter(field -> CronExpression.isValidExpression(valueOf(field)))
        .collect(toMap(Field::getName, JobRunScheduleTest::valueOf));
  }

  private static String valueOf(Field field) {
    try {
      return (String) field.get(null);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot read schedule constant: field=" + field.getName(), e);
    }
  }

  private static Duration reportImportLockAtMost() {
    try {
      SchedulerLock lock =
          ReportImportJob.class.getDeclaredMethod("schedule").getAnnotation(SchedulerLock.class);
      return DurationStyle.detectAndParse(lock.lockAtMostFor());
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Cannot read the report import lock", e);
    }
  }
}
