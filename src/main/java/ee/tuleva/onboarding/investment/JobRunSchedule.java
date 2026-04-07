package ee.tuleva.onboarding.investment;

public final class JobRunSchedule {

  private JobRunSchedule() {}

  public static final String TIMEZONE = "Europe/Tallinn";

  // Report import from S3 (every 5 min, 8:00–11:55 and once at 15:00)
  public static final String IMPORT_MORNING = "0 */5 8-11 * * *";
  public static final String IMPORT_AFTERNOON = "0 0 15 * * *";

  // Transaction command processing
  public static final String TRANSACTION_COMMAND = "0 * * * * *";

  // Backfill schedules (kept on cron, not event-driven)
  public static final String FEE_ACCRUAL_POSITION_BACKFILL = "0 25 12 12 3 *";
  public static final String LIMIT_CHECK_BACKFILL = "0 30 8 16 3 *";

  // Ad-hoc job trigger polling
  public static final String JOB_TRIGGER_POLL = "0 * * * * *";
}
