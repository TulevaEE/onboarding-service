package ee.tuleva.onboarding.aml.alert

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import static ee.tuleva.onboarding.aml.alert.AlertPartyType.*
import static ee.tuleva.onboarding.aml.alert.AmlAlertType.*
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.*

class TkfVolumeEvaluatorSpec extends Specification {

  def evaluator = new TkfVolumeEvaluator()

  static final Instant FLOW = Instant.parse("2026-06-10T00:00:00Z")

  TkfVolumeAggregate aggregate(Map args) {
    new TkfVolumeAggregate(
        "38001010000",
        args.depMonth as BigDecimal,
        args.redMonth as BigDecimal,
        args.containsKey('lastDeposit') ? args.lastDeposit as Instant : FLOW,
        args.containsKey('lastRedemption') ? args.lastRedemption as Instant : FLOW,
        "2026-06",
        args.containsKey('inCrm') ? args.inCrm as boolean : true,
        args.existing as boolean,
        args.review as Instant,
        PERSON)
  }

  @Unroll
  def "classifies TKF volume (live dashboard rules): inCrm=#inCrm existing=#existing depMonth=#depMonth redMonth=#redMonth -> #expected"() {
    given:
    def agg = aggregate(
        depMonth: depMonth, redMonth: redMonth, existing: existing, inCrm: inCrm, review: null)

    expect:
    evaluator.evaluate(agg).collect { [it.type(), it.direction()] } == expected

    where:
    inCrm | existing | depMonth | redMonth || expected
    // 15k new client, deposits/month, >= (requires presence in CRM)
    true  | false    | 15000.00 | 0.00     || [[TKF_VOLUME_15K_NEW_CLIENT, IN]]
    true  | false    | 14999.99 | 0.00     || []
    true  | true     | 15000.00 | 0.00     || []
    // not in CRM snapshot -> no 15k/30k (matches dashboard INNER JOIN, C1 limitation)
    false | false    | 15000.00 | 0.00     || []
    // 30k existing client, per direction, >=
    true  | true     | 30000.00 | 0.00     || [[TKF_VOLUME_30K_EXISTING_CLIENT, IN]]
    true  | true     | 0.00     | 30000.00 || [[TKF_VOLUME_30K_EXISTING_CLIENT, OUT]]
    true  | true     | 29999.99 | 29999.99 || []
  }

  @Unroll
  def "raises only the monthly alerts for monthly flows above 49k: existing=#existing depMonth=#depMonth redMonth=#redMonth -> #expected"() {
    given:
    def agg = aggregate(depMonth: depMonth, redMonth: redMonth, existing: existing, review: null)

    expect:
    evaluator.evaluate(agg).collect { [it.type(), it.direction()] } == expected

    where:
    existing | depMonth | redMonth || expected
    false    | 50000.00 | 0.00     || [[TKF_VOLUME_15K_NEW_CLIENT, IN]]
    true     | 50000.00 | 60000.00 || [[TKF_VOLUME_30K_EXISTING_CLIENT, IN], [TKF_VOLUME_30K_EXISTING_CLIENT, OUT]]
  }

  def "carries window amount and window key on each alert"() {
    given:
    def agg = aggregate(depMonth: 31000.00, redMonth: 32000.00, existing: true, review: null)

    when:
    def alerts = evaluator.evaluate(agg)

    then:
    with(alerts.find { it.type() == TKF_VOLUME_30K_EXISTING_CLIENT && it.direction() == IN }) {
      amount() == 31000.00
      windowKey() == "2026-06"
    }
    with(alerts.find { it.type() == TKF_VOLUME_30K_EXISTING_CLIENT && it.direction() == OUT }) {
      amount() == 32000.00
      windowKey() == "2026-06"
    }
  }

  @Unroll
  def "suppresses a monthly alert reviewed after the breaching deposit (review=#review -> #alertCount alerts)"() {
    given:
    def agg = aggregate(depMonth: 31000.00, redMonth: 0.00, existing: true, review: review)

    expect:
    evaluator.evaluate(agg).size() == alertCount

    where:
    review                                || alertCount
    null                                  || 1     // never reviewed -> alert
    Instant.parse("2026-06-09T00:00:00Z") || 1     // reviewed before the breaching deposit -> alert
    Instant.parse("2026-06-11T00:00:00Z") || 0     // reviewed after the breaching deposit -> suppress
  }

  @Unroll
  def "compares the manual review against the matching flow direction: depositReviewed=#dr redemptionReviewed=#rr -> #expected"() {
    given:
    def review = Instant.parse("2026-06-15T00:00:00Z")
    def beforeReview = Instant.parse("2026-06-10T00:00:00Z")
    def afterReview = Instant.parse("2026-06-20T00:00:00Z")
    def agg = aggregate(
        depMonth: 31000.00, redMonth: 31000.00, existing: true,
        lastDeposit: dr ? beforeReview : afterReview,
        lastRedemption: rr ? beforeReview : afterReview,
        review: review)

    expect:
    (evaluator.evaluate(agg).collect { it.direction() } as Set) == (expected as Set)

    where:
    dr    | rr    || expected
    false | false || [IN, OUT]
    true  | false || [OUT]
    false | true  || [IN]
    true  | true  || []
  }
}
