package ee.tuleva.onboarding.investment.kid;

import java.util.List;

interface KidCalculationNotifier {

  void notifyRuns(List<KidFundRun> runs);
}
