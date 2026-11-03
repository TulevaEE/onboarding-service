package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;
import java.util.Optional;

public interface OpenTradeSource {

  Optional<OpenTrades> openTrades(LocalDate navDate);
}
