package ee.tuleva.onboarding.investment;

public final class JobRunSchedule {

  private JobRunSchedule() {}

  public static final String TIMEZONE = "Europe/Tallinn";

  public static final String IMPORT_BUSINESS_HOURS = "0 */5 8-17 * * *";

  public static final String TRANSACTION_COMMAND = "0 * * * * *";

  public static final String TRACKING_DIFFERENCE_GAP_FILL = "0 0 19 * * MON-FRI";
  public static final String LIMIT_CHECK_GAP_FILL = "0 15 19 * * MON-FRI";
  public static final String INSTRUMENT_RETIREMENT = "0 30 19 * * MON-FRI";

  public static final String FEE_ACCRUAL_POSITION_BACKFILL = "0 25 7 12 3 *";

  public static final String PEVA_RAVA_PHASE_UPDATE = "0 0 7 * * MON-FRI";
  public static final String PEVA_RAVA_FLOW_RECALC = "0 30 17 * * MON-FRI";
  public static final String R16_FLOW_RECALC = "0 30 17 * * MON-FRI";

  public static final String RISK_INDICATOR_DAILY = "0 30 9 * * MON-FRI";

  public static final String JOB_TRIGGER_POLL = "0 * * * * *";
}
