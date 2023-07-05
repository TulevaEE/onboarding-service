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
  private BigDecimal weightedAverageFee;

  @JsonIgnore
  public boolean isSecondPillarFullyConverted() {
    return secondPillar.isFullyConverted();
  }

  @JsonIgnore
  public boolean isThirdPillarFullyConverted() {
    return thirdPillar.isFullyConverted();
  }

  @JsonIgnore
  public boolean isSecondPillarSelected() {
    return secondPillar.isSelectionComplete();
  }

  @Builder
  @Data
  public static class Conversion {

    private boolean transfersPartial;
    private boolean transfersComplete;
    private boolean selectionPartial;
    private boolean selectionComplete;
    private boolean pendingWithdrawal;
    private Boolean paymentComplete;
    private Amount contribution;
    private Amount subtraction;
    private BigDecimal weightedAverageFee;

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
    private BigDecimal lastYear;
  }
}
