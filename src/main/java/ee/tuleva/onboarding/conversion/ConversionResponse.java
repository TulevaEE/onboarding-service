package ee.tuleva.onboarding.conversion;

import java.math.BigDecimal;
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
    private Boolean paymentComplete;
    private BigDecimal yearToDateContribution;
  }
}
