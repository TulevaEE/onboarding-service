package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;

record KidRunFailedDigest(TulevaFund fund, String failedWith) implements KidDigestMessage {

  @Override
  public String text() {
    return """
        🔴 %s põhiteabe kuu stsenaariumid: arvutus ei jooksnud: %s
        Next: anna IT-le teada; logis on 'KID calculation failed: fund=%s'"""
        .formatted(fund.getCode(), failedWith, fund);
  }
}
