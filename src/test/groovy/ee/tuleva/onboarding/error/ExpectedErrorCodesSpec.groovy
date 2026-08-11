package ee.tuleva.onboarding.error

import spock.lang.Specification
import spock.lang.Unroll

class ExpectedErrorCodesSpec extends Specification {

  @Unroll
  def "#code is expected user behaviour: #expected"() {
    expect:
    ExpectedErrorCodes.isExpected(code) == expected

    where:
    code                             || expected
    "smart.id.user.refused"          || true
    "smart.id.account.not.found"     || true
    "smart.id.timeout"               || true
    "smart.id.validation.failed"     || true
    "mobile.id.cancelled"            || true
    "mobile.id.timeout"              || true
    "mobile.id.no.signal"            || true
    "mobile.id.certificates.revoked" || true
    "invalid.mandate.checks.missing" || true
    "new.user.flow.signup.error.email.duplicate" || true

    "mobile.id.configuration.error"  || false
    "mobile.id.communication.error"  || false
    "mobile.id.internal.error"       || false
    "mobile.id.error"                || false
    "smart.id.technical.error"       || false
    "epis.message.exception"         || false
    "something.completely.unknown"   || false
  }

  def "an absent error code is not treated as expected"() {
    expect:
    !ExpectedErrorCodes.isExpected(null)
  }
}
