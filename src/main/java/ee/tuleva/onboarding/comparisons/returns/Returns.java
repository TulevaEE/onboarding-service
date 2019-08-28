package ee.tuleva.onboarding.comparisons.returns;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class Returns {

    LocalDate from;
    List<Return> returns;

    @Value
    @Builder
    public static class Return {

        String key;
        double value; // TODO: migrate to BigDecimal
        Type type;

        public enum Type {PERSONAL, FUND, INDEX}
    }
}
