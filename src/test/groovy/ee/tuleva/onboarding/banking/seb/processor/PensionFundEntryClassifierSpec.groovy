package ee.tuleva.onboarding.banking.seb.processor

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.BankAdjustment
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.BankFee
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.InterestReceived
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.ManagementFeePayment
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.ManagementFeeRebate
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.RegistrarContribution
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.RegistrarPayout
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.TradeSettlement
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.Unclassified
import ee.tuleva.onboarding.banking.statement.BankStatementEntry
import ee.tuleva.onboarding.banking.statement.TransactionType
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR

class PensionFundEntryClassifierSpec extends Specification {

  static final String REGISTRAR_IBAN = "EE001234567890123477"
  static final String OTHER_IBAN = "EE001234567890123488"
  static final String SUBS_BUY =
      "DLA0544429/BDWTEIA/31426.66/34.085995776/Buy/ BlackRock Asset Management Ireland Ltd"

  def configuration = new SebAccountConfiguration(
      [(DEPOSIT_EUR): "EE001234567890123456"], "Tuleva Fondid AS", [REGISTRAR_IBAN])
  def classifier = new PensionFundEntryClassifier(new TradeSettlementParser(), configuration)

  @Unroll
  def "classifies #description"() {
    expect:
    classifier.classify(entry) == expected

    where:
    description                              | entry                                                                              | expected
    "detail-less interest"                   | entry("INTR", 2.00, null, null, "intress")                                         | new InterestReceived()
    "detail-less bank fee"                   | entry("FEES", -5.00, null, null, "teenustasu")                                     | new BankFee()
    "commission as bank fee"                 | entry("COMM", -0.48, null, null, "komisjonitasu")                                  | new BankFee()
    "detail-less adjustment"                 | entry("ADJT", 0.50, null, null, "korrektsioon")                                    | new BankAdjustment()
    "detail-less other as adjustment"        | entry("OTHR", 262.53, null, null, "Penalty CRED")                                  | new BankAdjustment()
    "bare subfund subscription"              | entry("SUBS", -1071209.00, null, null, SUBS_BUY)                                   | new TradeSettlement(FundTicker.ISHARES_DEVELOPED_WORLD_ESG_SCREENED, 31426.66)
    "enriched subfund subscription"          | entry("SUBS", -1071209.00, "BlackRock AM", OTHER_IBAN, SUBS_BUY)                   | new TradeSettlement(FundTicker.ISHARES_DEVELOPED_WORLD_ESG_SCREENED, 31426.66)
    "trade with unknown ticker"              | entry("TRAD", -100.00, null, null, "DLA1/ZZZZ GY/1/1/Buy/")                        | new Unclassified("unknown ticker")
    "kickback booking"                       | entry("BOOK", 4370.58, null, null, "Management fee kickback VP00001 02/2026")      | new ManagementFeeRebate()
    "registrar credit"                       | entry("RCDT", 1000000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "osakute laekumine") | new RegistrarContribution()
    "registrar debit"                        | entry("ICDT", -250000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "tagasivõtmine")    | new RegistrarPayout()
    "registrar credit with null code"        | entry(null, 500000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "laekumine")           | new RegistrarContribution()
    "management fee payment"                 | entry("ICDT", -742.34, "Tuleva Fondid AS", OTHER_IBAN, "Valitsemistasu 02/2026")   | new ManagementFeePayment()
    "management fee with wrong direction"    | entry("RCDT", 742.34, "Tuleva Fondid AS", OTHER_IBAN, "Valitsemistasu tagastus")   | new Unclassified("management fee with wrong direction")
    "book transfer without kickback"         | entry("BOOK", 100.00, null, null, "Internal transfer")                             | new Unclassified("subFamilyCode=BOOK")
    "unknown counterparty"                   | entry("RCDT", 15000.00, "Random Company OU", OTHER_IBAN, "ülekanne")               | new Unclassified("unknown counterparty")
    "detailed adjustment code"               | entry("OTHR", 99.99, "Random Company OU", OTHER_IBAN, "midagi")                    | new Unclassified("unknown counterparty")
    "detail-less unknown code"               | entry("XXXX", 10.00, null, null, "tundmatu")                                       | new Unclassified("subFamilyCode=XXXX")
    "detail-less null code"                  | entry(null, 10.00, null, null, "tundmatu")                                         | new Unclassified("subFamilyCode=null")
  }

  static BankStatementEntry entry(
      String subFamilyCode, BigDecimal amount, String counterpartyName, String counterpartyIban,
      String remittanceInformation) {
    def details = counterpartyName == null
        ? null
        : new BankStatementEntry.CounterPartyDetails(counterpartyName, counterpartyIban, null)
    new BankStatementEntry(
        details,
        amount,
        "EUR",
        amount >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        remittanceInformation,
        "entry-ref",
        null,
        subFamilyCode,
        null)
  }
}
