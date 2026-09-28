package ee.tuleva.onboarding.ledger

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.ledger.RegistrarPayoutReason.*

class RegistrarPayoutReasonSpec extends Specification {

  @Unroll
  def "reads '#remittance' as #reason"() {
    expect:
    fromRemittance(remittance) == reason

    where:
    remittance                                     || reason
    "Fondipensioni maksete lunastamine"            || FUND_PENSION
    "Ühekordsete maksete osakute lunastamine"      || ONE_OFF_WITHDRAWAL
    "Pensionifondi pärimisel osakute lunastamine"  || INHERITANCE
    "Vahetamise osakute lunastamine"               || FUND_SWITCH
    "Vahetamine PIK-i"                             || SWITCH_TO_PENSION_INVESTMENT_ACCOUNT
    "RAVA osakute lunastamine"                     || SECOND_PILLAR_EXIT
  }

  @Unroll
  def "normalises #variant before matching"() {
    expect:
    fromRemittance(remittance) == reason

    where:
    variant                              | remittance                                               || reason
    "soft hyphen and zero-width space"   | "Fondi­pensioni maksete​ lunastamine"          || FUND_PENSION
    "zero-width joiner and BOM"          | "﻿RAVA osakute‍ lunastamine"                   || SECOND_PILLAR_EXIT
    "decomposed umlaut"                  | "Ühekordsete maksete osakute lunastamine"          || ONE_OFF_WITHDRAWAL
    "upper case"                         | "PENSIONIFONDI PÄRIMISEL OSAKUTE LUNASTAMINE"            || INHERITANCE
    "repeated and non-breaking spaces"   | "  Vahetamise   osakute\t\nlunastamine  "           || FUND_SWITCH
    "lower case abbreviation"            | "vahetamine pik-i"                                       || SWITCH_TO_PENSION_INVESTMENT_ACCOUNT
  }

  @Unroll
  def "keeps #variant in its own unrecognised bucket"() {
    expect:
    fromRemittance(remittance) == UNRECOGNISED

    where:
    variant                                 | remittance
    "an unknown reason"                     | "Synthetic payout reason nobody mapped"
    "a known reason with extra text"        | "Fondipensioni maksete lunastamine tagasi"
    "blank text"                            | "   "
    "no text at all"                        | null
  }
}
