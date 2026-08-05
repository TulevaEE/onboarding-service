package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public interface ExecutedQuantitySummary {

  String getIsin();

  @Nullable BigDecimal getBought();

  @Nullable BigDecimal getSold();
}
