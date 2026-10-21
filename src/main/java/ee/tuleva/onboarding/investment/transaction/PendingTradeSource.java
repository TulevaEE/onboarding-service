package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;
import java.util.Optional;

public interface PendingTradeSource {

  Optional<PendingTrades> asOf(LocalDate asOfDate);
}
