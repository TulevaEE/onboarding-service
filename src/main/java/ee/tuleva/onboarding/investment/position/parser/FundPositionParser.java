package ee.tuleva.onboarding.investment.position.parser;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FundPositionParser {

  List<FundPosition> parse(List<Map<String, Object>> rawData, LocalDate reportDate);
}
