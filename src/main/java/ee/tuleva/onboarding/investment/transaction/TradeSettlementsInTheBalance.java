package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradeSettlementsInTheBalance(List<SettledInTheBalance> settlements) {}
