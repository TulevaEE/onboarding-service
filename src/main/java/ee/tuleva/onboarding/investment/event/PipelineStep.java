package ee.tuleva.onboarding.investment.event;

import java.util.List;

public final class PipelineStep {

  private PipelineStep() {}

  public static final String REPORT_IMPORT = "Report Import";
  public static final String POSITION_IMPORT = "Position Import";
  public static final String FEE_ACCRUAL_SYNC = "Fee Accrual Sync";
  public static final String NAV_CALCULATION = "NAV Calculation";
  public static final String LIMIT_CHECK = "Limit Check";
  public static final String TRACKING_DIFFERENCE = "Tracking Difference";

  public static final List<String> IMPORT_PIPELINE =
      List.of(REPORT_IMPORT, POSITION_IMPORT, FEE_ACCRUAL_SYNC);

  public static final List<String> NAV_PIPELINE = List.of(NAV_CALCULATION);
}
