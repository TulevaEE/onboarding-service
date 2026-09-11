package ee.tuleva.onboarding.banking.seb.processor

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.BankAdjustment
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.BankFee
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.InterestReceived
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.ManagementFeePayment
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.ManagementFeeRebate
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.OwnAccountTransfer
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.RegistrarContribution
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.RegistrarPayout
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.TradeSettlement
import ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier.Unclassified
import ee.tuleva.onboarding.banking.statement.BankStatementEntry
import ee.tuleva.onboarding.banking.statement.TransactionType
import ee.tuleva.onboarding.instrument.InstrumentReference
import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import spock.lang.Specification
import java.math.BigDecimal
import spock.lang.Unroll

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR
import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument

class PensionFundEntryClassifierSpec extends Specification {

  static final String REGISTRAR_IBAN = "EE001234567890123477"
  static final String OTHER_IBAN = "EE001234567890123488"
  static final String OWN_ACCOUNT_IBAN = "EE001234567890123499"
  static final String BANK_FEE_IBAN = "EE001234567890123411"
  static final String SUBS_BUY =
      "DLA0544429/BDWTEIA/31426.66/34.085995776/Buy/ BlackRock Asset Management Ireland Ltd"
  static final String REDM_SALE =
      "DLA0695877/BDWTEIA/59145/33.295/Sale/ BlackRock Asset Management Ireland Ltd"

  static final InstrumentReference DEVELOPED_WORLD = anInstrument()
      .isin("IE00BFG1TM61")
      .displayName("iShares Developed World Screened Index Fund")
      .yahooTicker("0P000152G5.F")
      .bloombergTicker("BDWTEIA")
      .build()

  def configuration = new SebAccountConfiguration(
      [(DEPOSIT_EUR): "EE001234567890123456"], "Tuleva Fondid AS", [REGISTRAR_IBAN],
      [OWN_ACCOUNT_IBAN], [BANK_FEE_IBAN])
  def instrumentReferenceService = Stub(InstrumentReferenceService) {
    findByTicker(_) >> Optional.empty()
    findByBloombergTicker(_) >> { String ticker ->
      ticker == "BDWTEIA" ? Optional.of(DEVELOPED_WORLD) : Optional.empty()
    }
  }
  def classifier = new PensionFundEntryClassifier(
      new TradeSettlementParser(instrumentReferenceService), configuration)

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
    "bare subfund subscription"              | entry("SUBS", -1071209.00, null, null, SUBS_BUY)                                   | developedWorldSettlement(31426.66)
    "enriched subfund subscription"          | entry("SUBS", -1071209.00, "BlackRock AM", OTHER_IBAN, SUBS_BUY)                   | developedWorldSettlement(31426.66)
    "trade with unknown ticker"              | entry("TRAD", -100.00, null, null, "DLA1/ZZZZ GY/1/1/Buy/")                        | new Unclassified("unknown ticker")
    "kickback booking"                       | entry("BOOK", 4370.58, null, null, "Management fee kickback VP00001 02/2026")      | new ManagementFeeRebate()
    "registrar credit"                       | entry("RCDT", 1000000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "osakute laekumine") | new RegistrarContribution()
    "registrar debit"                        | entry("ICDT", -250000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "tagasivõtmine")    | new RegistrarPayout()
    "registrar credit with null code"        | entry(null, 500000.00, "AS Pensionikeskus", REGISTRAR_IBAN, "laekumine")           | new RegistrarContribution()
    "management fee payment"                 | entry("ICDT", -742.34, "Tuleva Fondid AS", OTHER_IBAN, "Valitsemistasu 02/2026")   | new ManagementFeePayment()
    "management fee refund credited back"    | entry("RCDT", 742.34, "Tuleva Fondid AS", OTHER_IBAN, "Valitsemistasu tagastus")   | new ManagementFeeRebate()
    "book transfer without kickback"         | entry("BOOK", 100.00, null, null, "Internal transfer")                             | new Unclassified("subFamilyCode=BOOK")
    "unknown counterparty"                   | entry("RCDT", 15000.00, "Random Company OU", OTHER_IBAN, "ülekanne")               | new Unclassified("unknown counterparty")
    "detailed adjustment code"               | entry("OTHR", 99.99, "Random Company OU", OTHER_IBAN, "midagi")                    | new Unclassified("unknown counterparty")
    "detail-less unknown code"               | entry("XXXX", 10.00, null, null, "tundmatu")                                       | new Unclassified("subFamilyCode=XXXX")
    "detail-less null code"                  | entry(null, 10.00, null, null, "tundmatu")                                         | new Unclassified("subFamilyCode=null")
    "management company credit is a rebate"  | entry("RCDT", 100.00, "Tuleva Fondid AS", OTHER_IBAN, "muu ülekanne")              | new ManagementFeeRebate()
    "management company rebate transfer"     | entry("ESCT", 34720.54, "Tuleva Fondid AS", OTHER_IBAN, "TUK75 BR rebate")         | new ManagementFeeRebate()
    "management company expense debit"       | entry("BOOK", -1500.00, "Tuleva Fondid AS", OTHER_IBAN, "BR tasud")                | new ManagementFeePayment()
    "damage compensation from manager"       | entry("BOOK", 250.00, "Tuleva Fondid AS", OTHER_IBAN, "Kahju hüvitamine fondile")  | new ManagementFeeRebate()
    "subfund redemption settlement"          | entry("REDM", 993343.12, null, null, REDM_SALE)                                    | developedWorldSettlement(new BigDecimal("59145"))
    "own account transfer in"                | entry("ESCT", 1633975.32, "TULEVA MAAILMA AKTSIATE PENSIONIFOND", OWN_ACCOUNT_IBAN, "Ülekanne fondi teisele kontole") | new OwnAccountTransfer()
    "own account transfer out"               | entry("ICDT", -50000.00, "TULEVA MAAILMA AKTSIATE PENSIONIFOND", OWN_ACCOUNT_IBAN, "Ülekanne fondi teisele kontole") | new OwnAccountTransfer()
    "legacy bank service invoice"            | entry("ESCT", -140.00, "Swedbank AS", BANK_FEE_IBAN, "Arve nr 03-03-2026-3")       | new BankFee()
    "detailed ADJT from unknown party"       | entry("ADJT", 12.34, "Random Company OU", OTHER_IBAN, "korrektsioon")              | new Unclassified("unknown counterparty")
    "enriched kickback booking"              | entry("BOOK", 4370.58, "BlackRock AM", OTHER_IBAN, "Management fee kickback VP00001") | new ManagementFeeRebate()
    "entry with null remittance"             | entry("XXXX", 10.00, null, null, null)                                             | new Unclassified("subFamilyCode=XXXX")
    "management company zero amount is a rebate" | entry("BOOK", 0.00, "Tuleva Fondid AS", OTHER_IBAN, "nullülekanne")           | new ManagementFeeRebate()
    "registrar zero amount is a payout"      | entry("RCDT", 0.00, "AS Pensionikeskus", REGISTRAR_IBAN, "null-liikumine")         | new RegistrarPayout()
  }

  static TradeSettlement developedWorldSettlement(BigDecimal units) {
    new TradeSettlement("IE00BFG1TM61", "0P000152G5",
        "iShares Developed World Screened Index Fund", units)
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
