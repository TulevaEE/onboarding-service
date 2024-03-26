package ee.tuleva.onboarding.comparisons.returns.provider;

import ee.tuleva.onboarding.comparisons.returns.Returns;
import java.util.List;

public interface ReturnProvider {

  Returns getReturns(ReturnCalculationParameters returnCalculationParameters);

  List<String> getKeys();
}
