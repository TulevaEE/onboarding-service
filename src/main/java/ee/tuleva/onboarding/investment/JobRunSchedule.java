package ee.tuleva.onboarding.investment;

public final class JobRunSchedule {

  private JobRunSchedule() {}

  public static final String TIMEZONE = "Europe/Tallinn";

  // Report import from S3 — every 5 min during the business day. Must cover any time SEB might
  // send an intra-day correction (e.g. the 13:29 _uuendatud incident on 2026-04-10 that landed
  // in a 17-hour gap between the old 11:55 last morning fire and the next 08:00 morning slot).
  public static final String IMPORT_BUSINESS_HOURS = "0 */5 8-17 * * *";

  // Transaction command processing
  public static final String TRANSACTION_COMMAND = "0 * * * * *";

  // Limit check backstop. The check itself runs on NavCalculationCompleted; this fills any day
  // that produced no event, which the retired yearly backfill ("0 30 8 16 3 *") could leave
  // unnoticed for up to a year. Late enough that the day's imports and NAV run have landed.
  public static final String LIMIT_CHECK_DAILY_GAP_FILL = "0 30 18 * * MON-FRI";

  // Backfill schedules (kept on cron, not event-driven)
  public static final String FEE_ACCRUAL_POSITION_BACKFILL = "0 25 12 12 3 *";

  // EPIS PEVA/RAVA + R16 jobs; flow recalcs run after the daily NAV is available
  public static final String PEVA_RAVA_PHASE_UPDATE = "0 0 7 * * MON-FRI";
  public static final String PEVA_RAVA_FLOW_RECALC = "0 30 17 * * MON-FRI";
  public static final String R16_FLOW_RECALC = "0 30 17 * * MON-FRI";

  // SRI/SRRI risk indicators — evaluated every business day; the monthly Slack digest is a
  // notification decision inside the job, not a separate schedule.
  public static final String RISK_INDICATOR_DAILY = "0 30 9 * * MON-FRI";

  // Ad-hoc job trigger polling
  public static final String JOB_TRIGGER_POLL = "0 * * * * *";
}
