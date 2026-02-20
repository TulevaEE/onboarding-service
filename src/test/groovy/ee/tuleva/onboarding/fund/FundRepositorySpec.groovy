package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.fund.manager.FundManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE
import static ee.tuleva.onboarding.fund.Fund.FundStatus.LIQUIDATED
import static java.util.stream.Collectors.toList
import static java.util.stream.StreamSupport.stream

@DataJpaTest
class FundRepositorySpec extends Specification {

    @Autowired
    private TestEntityManager entityManager

    @Autowired
    private FundRepository repository

    def "persisting and finding by fund manager name works"() {
        given:
        def fundManager = FundManager.builder()
            .id(1)
            .name("Tuleva")
            .build()
        def fund = Fund.builder()
            .isin("EE000000000")
            .nameEstonian("Tuleva Maailma Aktsiate Pensionifond")
            .nameEnglish("Tuleva Maailma Aktsiate Pensionifond")
            .shortName("TUK75")
            .pillar(2)
            .equityShare(0.0)
            .managementFeeRate(new BigDecimal("0.0034"))
            .ongoingChargesFigure(new BigDecimal("0.005"))
            .status(ACTIVE)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build()
        entityManager.persist(fund)
        entityManager.flush()

        when:
        Iterable<Fund> funds = repository.findAllByFundManagerNameIgnoreCase("Tuleva")
        Fund persistedFund = stream(funds.spliterator(), false)
            .filter({ f -> f.isin == fund.isin })
            .findFirst()
            .get()

        then:
        persistedFund.id != null
        persistedFund.isin == fund.isin
        persistedFund.nameEstonian == fund.nameEstonian
        persistedFund.nameEnglish == fund.nameEnglish
        persistedFund.pillar == fund.pillar
        persistedFund.managementFeeRate == fund.managementFeeRate
        persistedFund.ongoingChargesFigure == fund.ongoingChargesFigure
        persistedFund.status == fund.status
        persistedFund.fundManager == fundManager
        persistedFund.inceptionDate == fund.inceptionDate

        cleanup:
        entityManager.clear()
    }

    def "finding by pillar works"() {
        given:
        def previousFundCount = repository.findAllByPillar(3).size()
        def fundManager = FundManager.builder()
            .id(1)
            .name("Tuleva")
            .build()
        def fund = Fund.builder()
            .isin("EE000000000")
            .nameEstonian("Tuleva Maailma Aktsiate Pensionifond")
            .nameEnglish("Tuleva Maailma Aktsiate Pensionifond")
            .shortName("TUK75")
            .pillar(3)
            .equityShare(0.0)
            .managementFeeRate(new BigDecimal("0.0034"))
            .ongoingChargesFigure(new BigDecimal("0.005"))
            .status(ACTIVE)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build()
        entityManager.persist(fund)
        entityManager.flush()

        when:
        Iterable<Fund> funds = repository.findAllByPillar(3)
        Fund persistedFund = stream(funds.spliterator(), false)
            .filter({ f -> f.isin == fund.isin })
            .findFirst()
            .get()

        then:
        persistedFund.id != null
        persistedFund.isin == fund.isin
        persistedFund.nameEstonian == fund.nameEstonian
        persistedFund.nameEnglish == fund.nameEnglish
        persistedFund.pillar == fund.pillar
        persistedFund.managementFeeRate == fund.managementFeeRate
        persistedFund.ongoingChargesFigure == fund.ongoingChargesFigure
        persistedFund.status == fund.status
        persistedFund.fundManager == fundManager
        persistedFund.inceptionDate == fund.inceptionDate

        when:
        Iterable<Fund> thirdPillarFunds = repository.findAllByPillar(3)

        then:
        thirdPillarFunds.size() == previousFundCount + 1
    }

    def "does not ignore inactive funds"() {
        given:
        Iterable<Fund> previousFunds = repository.findAll()
        List<Fund> previousInactiveFunds = stream(previousFunds.spliterator(), false)
            .filter({ fund -> fund.status != ACTIVE })
            .collect(toList())
        def previousInactiveFundCount = previousInactiveFunds.size()


        def fundManager = FundManager.builder()
            .id(1)
            .name("Tuleva")
            .build()
        def activeFund = Fund.builder()
            .isin("EE000000000")
            .nameEstonian("Tuleva Maailma Aktsiate Pensionifond")
            .nameEnglish("Tuleva Maailma Aktsiate Pensionifond")
            .shortName("TUK75")
            .pillar(2)
            .equityShare(0.0)
            .managementFeeRate(new BigDecimal("0.0034"))
            .ongoingChargesFigure(new BigDecimal("0.005"))
            .status(ACTIVE)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build()
        def inactiveFund = Fund.builder()
            .isin("EE000000002")
            .nameEstonian("Vana Fond")
            .nameEnglish("Some Old Fund")
            .shortName("OLD123")
            .pillar(2)
            .equityShare(0.0)
            .managementFeeRate(new BigDecimal("0.0123"))
            .ongoingChargesFigure(new BigDecimal("0.0123"))
            .status(LIQUIDATED)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build()
        entityManager.persist(activeFund)
        entityManager.persist(inactiveFund)
        entityManager.flush()

        when:
        Iterable<Fund> funds = repository.findAll()
        List<Fund> inactiveFunds = stream(funds.spliterator(), false)
            .filter({ fund -> fund.status != ACTIVE })
            .collect(toList())

        then:
        inactiveFunds.size() == previousInactiveFundCount + 1
    }

    def "can find inactive funds by isin"() {
        given:
        def fundManager = FundManager.builder()
            .id(1)
            .name("Tuleva")
            .build()
        def inactiveFund = Fund.builder()
            .isin("EE000000002")
            .nameEstonian("Vana Fond")
            .nameEnglish("Some Old Fund")
            .shortName("AE123")
            .pillar(2)
            .equityShare(0.0)
            .managementFeeRate(new BigDecimal("0.0123"))
            .ongoingChargesFigure(new BigDecimal("0.0123"))
            .status(LIQUIDATED)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build()
        entityManager.persist(inactiveFund)
        entityManager.flush()

        when:
        Fund fund = repository.findByIsin(inactiveFund.isin)

        then:
        fund.isin == inactiveFund.isin
    }
}
