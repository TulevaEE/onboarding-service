package ee.tuleva.onboarding.conversion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ConversionResponse {

  private Conversion secondPillar;
  private Conversion thirdPillar;

  @JsonIgnore
  public boolean isSecondPillarFullyConverted() {
    return secondPillar.isFullyConverted();
  }

  @JsonIgnore
  public boolean isThirdPillarFullyConverted() {
    return thirdPillar.isFullyConverted();
  }

  @Builder
  @Data
  public static class Conversion {

    private boolean transfersComplete;
    private boolean selectionComplete;
    private boolean pendingWithdrawal;
    private Boolean paymentComplete;
    private Amount contribution;
    private Amount subtraction;

    @JsonIgnore
    public boolean isFullyConverted() {
      return transfersComplete && selectionComplete;
    }
  }

  @Builder
  @Data
  public static class Amount {

    private BigDecimal total;
    private BigDecimal yearToDate;
  }
}
