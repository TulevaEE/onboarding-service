package ee.tuleva.onboarding.investment.check.health

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100

@DataJpaTest
class UnitReconciliationThresholdRepositorySpec extends Specification {

    @Autowired
    private UnitReconciliationThresholdRepository repository

    def "seeds rows for all NAV-calculated funds"() {
        expect:
        repository.findByFundCode(TKF100).isPresent()
        repository.findByFundCode(TUK75).isPresent()
        repository.findByFundCode(TUK00).isPresent()
        repository.findByFundCode(TUV100).isPresent()
    }

    def "TKF100 has both warning and fail thresholds configured"() {
        when:
        def threshold = repository.findByFundCode(TKF100).get()

        then:
        threshold.warningUnits == 0.02
        threshold.failUnits == 0.5
    }

    def "pillar 2/3 funds default to notify-only (no fail threshold)"() {
        expect:
        with(repository.findByFundCode(fund).get()) {
            warningUnits == 0
            failUnits == null
        }

        where:
        fund << [TUK75, TUK00, TUV100]
    }
}
