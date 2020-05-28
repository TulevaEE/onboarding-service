package ee.tuleva.onboarding.conversion;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class ConversionResponse {

    private Conversion secondPillar;
    private Conversion thirdPillar;

    @Builder
    @Data
    public static class Conversion {
        private boolean transfersComplete;
        private boolean selectionComplete;
        private Boolean paymentComplete;
        private Amount contribution;
        private Amount subtraction;

    }

    @Builder
    @Data
    public static class Amount {
        private BigDecimal total;
        private BigDecimal yearToDate;
    }
}
