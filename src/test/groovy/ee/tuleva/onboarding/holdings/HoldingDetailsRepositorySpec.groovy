package ee.tuleva.onboarding.holdings

import ee.tuleva.onboarding.holdings.persistence.HoldingDetail
import ee.tuleva.onboarding.holdings.persistence.Region
import ee.tuleva.onboarding.holdings.persistence.Sector
import ee.tuleva.onboarding.holdings.persistence.HoldingDetailsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.time.LocalDate

@DataJpaTest
class HoldingDetailsRepositorySpec extends Specification{
    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private HoldingDetailsRepository repository

    def "persisting and finding by last date works"() {
        given:
        def testDate = LocalDate.of(2014, 12, 31)
        def holdingDetail = HoldingDetail.builder()
        .symbol("MSFT")
        .country("USA")
        .currency("USD")
        .securityName("Microsoft Corp")
        .weighting(2.76)
        .numberOfShare(7628806000)
        .shareChange(0)
        .marketValue(1367158323260)
        .sector(Sector.BASIC_MATERIALS)
        .holdingYtdReturn(11.02)
        .region(Region.AFRICA)
        .isin("US5949181045")
        .firstBoughtDate(LocalDate.of(2014, 12, 31))
        .createdDate(testDate)
        .build()
        entityManager.persistAndFlush(holdingDetail)

        when:
        def persistDetail = repository.findFirstByOrderByCreatedDateDesc()
        then:
        persistDetail.id != null
        persistDetail.symbol == holdingDetail.symbol
        persistDetail.country == holdingDetail.country
        persistDetail.currency == holdingDetail.currency
        persistDetail.securityName == holdingDetail.securityName
        persistDetail.weighting == holdingDetail.weighting
        persistDetail.numberOfShare == holdingDetail.numberOfShare
        persistDetail.shareChange == holdingDetail.shareChange
        persistDetail.marketValue == holdingDetail.marketValue
        persistDetail.sector == holdingDetail.sector
        persistDetail.holdingYtdReturn == holdingDetail.holdingYtdReturn
        persistDetail.region == holdingDetail.region
        persistDetail.isin == holdingDetail.isin
        persistDetail.firstBoughtDate == holdingDetail.firstBoughtDate
        persistDetail.createdDate == testDate
    }

    def "should return null if no entry exists"() {
        given:
        when:
        def persistDetail = repository.findFirstByOrderByCreatedDateDesc()
        then:
        persistDetail == null
    }

    def "should be able to save with null fields"() {
        given:
        def testDate = LocalDate.of(2014, 12, 31)
        def holdingDetail = HoldingDetail.builder()
            .securityName("Microsoft Corp")
            .createdDate(testDate)
            .build()
        entityManager.persistAndFlush(holdingDetail)

        when:
        def persistDetail = repository.findFirstByOrderByCreatedDateDesc()
        then:
        persistDetail.id != null
        persistDetail.securityName == holdingDetail.securityName
        persistDetail.createdDate == testDate
    }
}
