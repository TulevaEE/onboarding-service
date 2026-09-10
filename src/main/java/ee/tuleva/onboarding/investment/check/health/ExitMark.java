package ee.tuleva.onboarding.investment.check.health;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record ExitMark(BigDecimal executedPrice, @Nullable PublishedPrice published) {

  record PublishedPrice(BigDecimal price, LocalDate date) {}
}
