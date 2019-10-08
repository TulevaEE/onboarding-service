package ee.tuleva.onboarding.statistics

import static ee.tuleva.onboarding.statistics.ThirdPillarStatistics.*

class ThirdPillarStatisticsFixture {

    static ThirdPillarStatisticsBuilder sampleThirdPillarStatistics() {
        builder()
            .recurringPayment(100.0)
            .singlePayment(null)
    }

}
