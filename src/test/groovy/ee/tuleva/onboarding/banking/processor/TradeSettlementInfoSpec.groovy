package ee.tuleva.onboarding.banking.processor

import ee.tuleva.onboarding.ledger.FundBankLedger.TradeSettlementDetails
import ee.tuleva.onboarding.banking.processor.TradeSettlementParser.TradeSettlementInfo
import spock.lang.Specification
import spock.lang.Unroll

class TradeSettlementInfoSpec extends Specification {

  @Unroll
  def "units take the trade's direction from its cash: #trade"() {
    expect:
    developedWorld(statedUnits).signedUnits(cashAmount) == signedUnits

    where:
    trade                                           | cashAmount   | statedUnits | signedUnits
    "a buy pays cash and adds units"                | -34448.28    | 1000        | 1000.00000
    "a sell receives cash and removes units"        | 50000.00     | 1450.25     | -1450.25000
    "a sell stated with negative units removes them" | 50000.00     | -1450.25    | -1450.25000
    "a buy stated with negative units adds them"    | -34448.28    | -1000       | 1000.00000
    "a zero-cash settlement adds units"             | 0.00         | 11704       | 11704.00000
    "units round to five decimals"                  | -100.00      | 1.234565    | 1.23457
  }

  static TradeSettlementInfo developedWorld(BigDecimal units) {
    new TradeSettlementInfo(new TradeSettlementDetails("DLA0544429", "IE00BFG1TM61", "BDWTEIA", "iShares Developed World Screened Index Fund", 34.085995776), units)
  }
}
