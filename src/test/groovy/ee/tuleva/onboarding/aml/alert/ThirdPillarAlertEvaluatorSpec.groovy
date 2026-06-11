package ee.tuleva.onboarding.aml.alert

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.*
import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransactionBuilder

class ThirdPillarAlertEvaluatorSpec extends Specification {

  def evaluator = new ThirdPillarAlertEvaluator()

  @Unroll
  def "classifies III pillar transaction: source=#source type=#type value=#value -> #expected"() {
    given:
    AnalyticsThirdPillarTransaction transaction = exampleTransactionBuilder()
        .transactionSource(source)
        .transactionType(type)
        .transactionValue(value)
        .build()

    expect:
    evaluator.evaluate(transaction) == expected

    where:
    source                                                    | type                              | value     || expected
    'Osakute väljalase isikult laekumiste alusel'             | 'irrelevant'                      | 6001.00   || [III_PILLAR_DEPOSIT_PERSON]
    'Osakute väljalase isikult laekumiste alusel'             | 'irrelevant'                      | 6000.99   || []
    'Osakute väljalase tööandjalt laekumiste alusel'          | 'irrelevant'                      | 6001.00   || [III_PILLAR_DEPOSIT_EMPLOYER]
    'Osakute väljalase tööandjalt laekumiste alusel'          | 'irrelevant'                      | 6000.99   || []
    'Osakute vahetamine (väljalase) 3. sammas'                | 'irrelevant'                      | 20001.00  || [III_PILLAR_DEPOSIT_TRANSFER]
    'Osakute vahetamine (väljalase) 3. sammas'                | 'irrelevant'                      | 20000.99  || []
    'Osakute väljalase kindlustuslepingust laekumiste alusel' | 'irrelevant'                      | 20001.00  || [III_PILLAR_DEPOSIT_INSURANCE]
    'Osakute väljalase kindlustuslepingust laekumiste alusel' | 'irrelevant'                      | 20000.99  || []
    'someSource'                                              | 'Osakute ülekanne'                | 20001.00  || [III_PILLAR_WITHDRAWAL]
    'someSource'                                              | 'Broneeritud osakute kustutamine' | 20001.00  || [III_PILLAR_WITHDRAWAL]
    'someSource'                                              | 'Osakute ülekanne'                | 20000.99  || []
    'Pärimine'                                                | 'Osakute ülekanne'                | 999999.00 || []
    'someSource'                                              | 'someType'                        | 999999.00 || []
  }
}
