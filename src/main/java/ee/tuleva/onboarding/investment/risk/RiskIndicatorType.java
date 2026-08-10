package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.fund.TulevaFund;

public enum RiskIndicatorType {
  SRI,
  SRRI;

  /**
   * Pension products are carved out of PRIIPs by (EU) 1286/2014 art 2(2)(e), so pillar II and III
   * funds keep the UCITS-style SRRI of CESR/10-673. The savings fund is a PRIIPs product and gets
   * the SRI of RTS (EU) 2017/653.
   */
  public static RiskIndicatorType forFund(TulevaFund fund) {
    return fund.isSavingsFund() ? SRI : SRRI;
  }
}
