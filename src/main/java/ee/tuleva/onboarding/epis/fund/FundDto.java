package ee.tuleva.onboarding.epis.fund;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FundDto {

  private String isin;
  private String name;
  private String shortName;
  private int pillar;
  private FundStatus status;

  public enum FundStatus {
    ACTIVE,
    LIQUIDATED,
    SUSPENDED,
    CONTRIBUTIONS_FORBIDDEN,
    PAYOUTS_FORBIDDEN
  }
}
