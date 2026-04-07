package ee.tuleva.onboarding.investment.event;

import java.util.List;

public final class PipelineStep {

  private PipelineStep() {}

  public static final String REPORT_IMPORT = "Report Import";
  public static final String POSITION_IMPORT = "Position Import";
  public static final String FEE_ACCRUAL_SYNC = "Fee Accrual Sync";
  public static final String LIMIT_CHECK = "Limit Check";
  public static final String TRACKING_DIFFERENCE = "Tracking Difference";

  public static final List<String> ALL =
      List.of(REPORT_IMPORT, POSITION_IMPORT, FEE_ACCRUAL_SYNC, LIMIT_CHECK, TRACKING_DIFFERENCE);
}
