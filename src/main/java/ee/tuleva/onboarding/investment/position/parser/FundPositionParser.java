package ee.tuleva.onboarding.investment.position.parser;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.io.InputStream;
import java.util.List;

public interface FundPositionParser {

  List<FundPosition> parse(InputStream inputStream);
}
