package ee.tuleva.onboarding.investment.transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CashSection(
    CashSourceReport sources, List<NettedForecast> forecasts, R16Reserve r16) {

  List<CalculationWarning> warnings() {
    return sources.warnings();
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>(sources.payload());
    List<NettedForecast> inPlay = forecasts.stream().filter(NettedForecast::isInPlay).toList();
    if (!inPlay.isEmpty()) {
      map.put("forecasts", inPlay.stream().map(NettedForecast::payload).toList());
    }
    if (r16.basis() != R16Reserve.Basis.NO_R16_THIS_MONTH) {
      map.put("r16", r16.payload());
    }
    return map;
  }
}
