package ee.tuleva.onboarding.aml.risklevel;

import java.util.List;

interface RiskReader {
  List<RiskLevelResult> getHighRiskRows();

  List<RiskLevelResult> getMediumRiskRowsSample(double individualSelectionProbability);

  void refreshMaterializedView();
}
