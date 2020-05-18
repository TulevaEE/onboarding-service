package ee.tuleva.onboarding.holdings

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
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
        ._id("E0USA00A05")
        ._externalId("594918104")
        .symbol("MSFT")
        .country_id("USA")
        .cusip("594918104")
        .currency_id("USD")
        .securityName("Microsoft Corp")
        .legalType("E")
        .weighting(2.76)
        .numberOfShare(7628806000)
        .shareChange(0)
        .marketValue(1367158323260)
        .sector(11)
        .holdingYtdReturn(11.02)
        .region(1)
        .isin("US5949181045")
        .styleBox(3)
        .sedol("2588173")
        .firstBoughtDate(LocalDate.of(2014, 12, 31))
        .createdDate(testDate)
        .build()
        entityManager.persist(holdingDetail)
        entityManager.flush()

        when:
        def persistDetail = repository.findFirstByOrderByCreatedDateDesc()
        then:
        persistDetail.id != null
        persistDetail._id == holdingDetail._id
        persistDetail._externalId == holdingDetail._externalId
        persistDetail.symbol == holdingDetail.symbol
        persistDetail.country_id == holdingDetail.country_id
        persistDetail.cusip == holdingDetail.cusip
        persistDetail.currency_id == holdingDetail.currency_id
        persistDetail.securityName == holdingDetail.securityName
        persistDetail.legalType == holdingDetail.legalType
        persistDetail.weighting == holdingDetail.weighting
        persistDetail.numberOfShare == holdingDetail.numberOfShare
        persistDetail.shareChange == holdingDetail.shareChange
        persistDetail.marketValue == holdingDetail.marketValue
        persistDetail.sector == holdingDetail.sector
        persistDetail.holdingYtdReturn == holdingDetail.holdingYtdReturn
        persistDetail.region == holdingDetail.region
        persistDetail.isin == holdingDetail.isin
        persistDetail.styleBox == holdingDetail.styleBox
        persistDetail.sedol == holdingDetail.sedol
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
}
