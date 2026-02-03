package ee.tuleva.onboarding.investment;

public final class JobRunSchedule {

  private JobRunSchedule() {}

  public static final String TIMEZONE = "Europe/Tallinn";

  // Fee calculation
  public static final String FEE_CALCULATION = "0 0 6 * * *";

  // Raw data import from S3 to database (every 5 min from :00 to :25 to catch late reports)
  public static final String IMPORT_MORNING = "0 0,5,10,15,20,25 11 * * *";
  public static final String IMPORT_AFTERNOON = "0 0 15 * * *";

  // Parsing from database to domain entities (2 min after each import attempt)
  public static final String PARSE_MORNING = "0 2,7,12,17,22,27 11 * * *";
  public static final String PARSE_AFTERNOON = "0 5 15 * * *";

  // Position calculation (30 minutes after import, after parsing completes)
  public static final String CALCULATE_MORNING = "0 30 11 * * *";
  public static final String CALCULATE_AFTERNOON = "0 30 15 * * *";
}
