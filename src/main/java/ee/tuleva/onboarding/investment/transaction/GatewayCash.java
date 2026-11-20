package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
sealed interface GatewayCash {

  Map<String, Object> payload();

  record CurrentDayBalance(BigDecimal balance, Instant fetchedAt) implements GatewayCash {

    @Override
    public Map<String, Object> payload() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", "CURRENT_DAY_BALANCE");
      map.put("balance", balance.toPlainString());
      map.put("fetchedAt", fetchedAt.toString());
      return map;
    }
  }

  record NoCurrentDayReportToday() implements GatewayCash {

    @Override
    public Map<String, Object> payload() {
      return Map.of("status", "NO_CURRENT_DAY_REPORT_TODAY");
    }
  }

  record NotTodaysRun() implements GatewayCash {

    @Override
    public Map<String, Object> payload() {
      return Map.of("status", "NOT_TODAYS_RUN");
    }
  }

  record Unreadable() implements GatewayCash {

    @Override
    public Map<String, Object> payload() {
      return Map.of("status", "CURRENT_DAY_REPORT_UNREADABLE");
    }
  }
}
