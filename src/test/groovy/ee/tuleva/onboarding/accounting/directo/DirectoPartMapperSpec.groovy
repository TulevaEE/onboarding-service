package ee.tuleva.onboarding.accounting.directo

import ee.tuleva.onboarding.ledger.JournalEntryPart
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EQUITY
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EXPENSE
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.OFF_BALANCE

class DirectoPartMapperSpec extends Specification {

  static final List<DirectoAccount> CHART = [
      new DirectoAccount("100100", "Bank account", "0", null, null),
      new DirectoAccount("200100", "Trade payables", "1", null, null),
      new DirectoAccount("212301", "VÕLAD TÖÖTAJATELE", "1", null, null),
      new DirectoAccount("212302", "Puhkusetasu kohustus", "1", null, null),
      new DirectoAccount("212303", "Aruandvad isikud", "1", null, null),
      new DirectoAccount("212304", "Töövõtjate tasud", "1", null, null),
      new DirectoAccount("212305", "Erisoodustuse maks", "1", null, null),
      new DirectoAccount("212306", "Päevarahad", "1", null, null),
      new DirectoAccount("212307", "Lähetuskulud maksmata", "1", null, null),
      new DirectoAccount("212308", "Haigushüvitised", "1", null, null),
      new DirectoAccount("212310", "Arvestatud palk", "1", null, null),
      new DirectoAccount("212311", "Palgavõlad", "1", null, null),
      new DirectoAccount("212401", "Renamed liability", "1", null, null),
      new DirectoAccount("500100", "Office rent", "4", null, null),
      new DirectoAccount("500300", "Töötajate koolitus", "4", null, null),
      new DirectoAccount("540100", "Salaries", "4", null, null),
  ]

  static final List<DirectoObject> OBJECTS = [
      new DirectoObject("TEAM1", "10"),
      new DirectoObject("C9", "30"),
      new DirectoObject("40015", "40"),
      new DirectoObject("E001", "50"),
  ]

  def mapper = new DirectoPartMapper()

  @Unroll
  def "requiredFieldMissingFailsFast #field"() {
    when:
    mapper.map(new DirectoBook(accounts, objects, transactions))

    then:
    thrown(IllegalArgumentException)

    where:
    field             | accounts                                              | objects                          | transactions
    "type"            | CHART                                                 | OBJECTS                          | [document(null, "1", "2026-01-05T00:00:00", balancedRows())]
    "number"          | CHART                                                 | OBJECTS                          | [document("FIN", null, "2026-01-05T00:00:00", balancedRows())]
    "date"            | CHART                                                 | OBJECTS                          | [document("FIN", "1", null, balancedRows())]
    "blank date"      | CHART                                                 | OBJECTS                          | [document("FIN", "1", " ", balancedRows())]
    "rows"            | CHART                                                 | OBJECTS                          | [document("FIN", "1", "2026-01-05T00:00:00", null)]
    "rows.account"    | CHART                                                 | OBJECTS                          | [document("FIN", "1", "2026-01-05T00:00:00", [row(null, 5.00, null), row("100100", null, 5.00)])]
    "accounts.code"   | [new DirectoAccount(null, "Bank account", "0", null, null)] | OBJECTS                    | []
    "accounts.class"  | [new DirectoAccount("100100", "Bank account", null, null, null)] | OBJECTS               | []
    "objects.code"    | CHART                                                 | [new DirectoObject(null, "50")] | []
  }

  @Unroll
  def "classMapsToAccountType #accountClass"() {
    when:
    def book = mapper.map(new DirectoBook([new DirectoAccount("100100", "Some account", accountClass, null, null)], [], []))

    then:
    book.accounts()*.accountType() == [accountType]

    where:
    accountClass || accountType
    "0"          || ASSET
    "1"          || LIABILITY
    "2"          || EQUITY
    "3"          || INCOME
    "4"          || EXPENSE
    "5"          || OFF_BALANCE
  }

  @Unroll
  def "aDocumentIsPayrollProtectedWhen #reason"() {
    given:
    def protectedRow = new DirectoRow(account, 80.00, null, object, project, supplier, customer, null)
    def counterRow = new DirectoRow("100100", null, 80.00, null, null, null, null, null)
    def book = new DirectoBook(CHART, OBJECTS, [document(type, "7", "2026-01-10T00:00:00", [counterRow, protectedRow])])

    when:
    def mapped = mapper.map(book)

    then:
    mapped.protectedSourceKeys() == (isProtected ? ["${type}:7:2026-01-10".toString()] : []) as Set
    mapped.parts()*.sourceKey() == [isProtected ? "PAYROLL:2026-01" : "${type}:7:2026-01-10".toString()]

    where:
    reason                                  | type   | account  | object         | supplier | customer | project   || isProtected
    "the document type is payroll"          | "PALK" | "500100" | null           | null     | null     | null      || true
    "an account starts with 54"             | "FIN"  | "540100" | null           | null     | null     | null      || true
    "a liability names employees"           | "FIN"  | "212301" | null           | null     | null     | null      || true
    "a liability names holiday pay"         | "FIN"  | "212302" | null           | null     | null     | null      || true
    "a liability names accountable persons" | "FIN"  | "212303" | null           | null     | null     | null      || true
    "a liability names contractors"         | "FIN"  | "212304" | null           | null     | null     | null      || true
    "a liability names fringe benefits"     | "FIN"  | "212305" | null           | null     | null     | null      || true
    "a liability names daily allowances"    | "FIN"  | "212306" | null           | null     | null     | null      || true
    "a liability names business trips"      | "FIN"  | "212307" | null           | null     | null     | null      || true
    "a liability names sick pay"            | "FIN"  | "212308" | null           | null     | null     | null      || true
    "a liability names salary"              | "FIN"  | "212310" | null           | null     | null     | null      || true
    "a liability names salary payables"     | "FIN"  | "212311" | null           | null     | null     | null      || true
    "a payroll liability was renamed"       | "FIN"  | "212401" | null           | null     | null     | null      || true
    "an employee is among the objects"      | "OST"  | "500100" | "TEAM1, E001"  | null     | null     | null      || true
    "the supplier is an employee"           | "OST"  | "500100" | null           | "E001"   | null     | null      || true
    "the customer is an employee"           | "ARVE" | "500100" | null           | null     | "E001"   | null      || true
    "an object is unknown"                  | "OST"  | "500100" | "TEAM1,X404"   | null     | null     | null      || true
    "the supplier is unknown"               | "OST"  | "500100" | null           | "40016"  | null     | null      || true
    "the customer is unknown"               | "ARVE" | "500100" | null           | null     | "C10"    | null      || true
    "nothing matches"                       | "OST"  | "500100" | "TEAM1"        | "40015"  | "C9"     | null      || false
    "only the project is unknown"           | "OST"  | "500100" | " TEAM1 , "    | null     | null     | "PRJ-NEW" || false
    "an expense names employees"            | "OST"  | "500300" | null           | null     | null     | null      || false
    "a liability's name has no match"       | "OST"  | "200100" | null           | null     | null     | null      || false
  }

  @Unroll
  def "aPersonalCodeInAnyDimensionStopsTheEntity #dimension"() {
    given:
    def row = new DirectoRow("500100", 80.00, null, object, project, supplier, customer, null)
    def book = new DirectoBook(CHART, OBJECTS, [document("OST", "7", "2026-01-10T00:00:00", [row, new DirectoRow("100100", null, 80.00, null, null, null, null, null)])])

    when:
    mapper.map(book)

    then:
    thrown(IllegalStateException)

    where:
    dimension  | object              | supplier      | customer      | project
    "object"   | "TEAM1,38001085718" | null          | null          | null
    "supplier" | null                | "38001085718" | null          | null
    "customer" | null                | null          | "38001085718" | null
    "project"  | null                | null          | null          | "38001085718"
  }

  def "theSummaryOfAMonthCoversEveryProtectedDocumentOfIt"() {
    given:
    def payroll = document("PALK", "4", "2026-01-31T00:00:00", [
        new DirectoRow("540100", 3000.00, null, "E001", null, null, null, null),
        new DirectoRow("212301", null, 3000.00, "E001", null, null, null, null)])
    def payout = document("FIN", "9", "2026-01-15T00:00:00", [
        new DirectoRow("212301", 2500.00, null, "E001", null, null, null, null),
        new DirectoRow("100100", null, 2500.00, null, null, null, null, null)])
    def rent = document("OST", "1", "2026-01-20T00:00:00", [
        new DirectoRow("500100", 100.00, null, null, null, "40015", null, null),
        new DirectoRow("200100", null, 100.00, null, null, "40015", null, null)])

    when:
    def mapped = mapper.map(new DirectoBook(CHART, OBJECTS, [payroll, payout, rent]))

    then:
    mapped.parts()*.sourceKey() == ["OST:1:2026-01-20", "PAYROLL:2026-01"]
    mapped.protectedSourceKeys() == ["PALK:4:2026-01-31", "FIN:9:2026-01-15"] as Set
    with(mapped.parts().find { it.sourceKey() == "PAYROLL:2026-01" } as JournalEntryPart) {
      documentType() == "PAYROLL_SUMMARY"
      date().toString() == "2026-01-31"
      lines()*.accountCode() == ["100100", "212301", "540100"]
      lines()*.amount() == [-2500.00, -500.00, 3000.00]
    }
  }

  private static DirectoTransaction document(String type, String number, String date, List<DirectoRow> rows) {
    new DirectoTransaction(type, number, date, rows)
  }

  private static List<DirectoRow> balancedRows() {
    [row("100100", 5.00, null), row("300100", null, 5.00)]
  }

  private static DirectoRow row(String account, BigDecimal debit, BigDecimal credit) {
    new DirectoRow(account, debit, credit, null, null, null, null, null)
  }
}
