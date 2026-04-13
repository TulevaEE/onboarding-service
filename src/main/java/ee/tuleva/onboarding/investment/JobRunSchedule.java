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

  // Backfill schedules (kept on cron, not event-driven)
  public static final String FEE_ACCRUAL_POSITION_BACKFILL = "0 25 12 12 3 *";
  public static final String LIMIT_CHECK_BACKFILL = "0 30 8 16 3 *";

  // Ad-hoc job trigger polling
  public static final String JOB_TRIGGER_POLL = "0 * * * * *";
}
