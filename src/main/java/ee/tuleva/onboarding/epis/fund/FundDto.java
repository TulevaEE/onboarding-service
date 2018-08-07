package ee.tuleva.onboarding.epis.fund;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FundDto {

  private String isin;
  private String name;
  private String shortName;
  private int pillar;
  private FundStatus status;

  public enum FundStatus {
    ACTIVE, LIQUIDATED, SUSPENDED, CONTRIBUTIONS_FORBIDDEN, PAYOUTS_FORBIDDEN
  }
}
