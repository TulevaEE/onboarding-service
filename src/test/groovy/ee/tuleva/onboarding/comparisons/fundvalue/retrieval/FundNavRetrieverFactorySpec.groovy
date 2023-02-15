package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE
import static ee.tuleva.onboarding.fund.FundFixture.*

class FundNavRetrieverFactorySpec extends Specification {

  FundRepository fundRepository = Mock()
  EpisService episService = Mock()

  FundNavRetrieverFactory fundNavRetrieverFactory =
      new FundNavRetrieverFactory(fundRepository, episService)


  def "creates fund nav retrievers for all active funds"() {
    given:
    def sampleFunds = [tuleva2ndPillarBondFund, tuleva2ndPillarStockFund,
                       tuleva3rdPillarFund, lhv2ndPillarFund, lhv3rdPillarFund]
    fundRepository.findAllByStatus(ACTIVE) >> sampleFunds
    when:
    def retrievers = fundNavRetrieverFactory.createAll()
    then:
    retrievers.eachWithIndex { retriever, i ->
      assert retriever.key == sampleFunds[i].isin
    }
  }
}
