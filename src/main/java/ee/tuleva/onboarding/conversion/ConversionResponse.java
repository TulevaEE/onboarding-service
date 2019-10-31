package ee.tuleva.onboarding.conversion;

import lombok.Builder;
import lombok.Data;

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
    }

}
