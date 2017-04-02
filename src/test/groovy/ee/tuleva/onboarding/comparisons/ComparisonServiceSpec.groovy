package ee.tuleva.onboarding.comparisons


import spock.lang.Specification

class ComparisonServiceSpec extends Specification {

    def service = new ComparisonService()

    def "feeSaved calculations work" (Map currentCapitals, Map managementFeeRates, String activeIsin, int age, BigDecimal monthlyWage, BigDecimal feeSaved) {
        expect:
        ComparisonCommand command = new ComparisonCommand()
        command.setCurrentCapitals(currentCapitals)
        command.setAge(age)
        command.setMonthlyWage(monthlyWage)
        command.setManagementFeeRates(managementFeeRates)
        command.setActiveFundIsin(activeIsin)

        service.calculateTotalFeeSaved(command) == feeSaved

        where:
        currentCapitals          | managementFeeRates                             | activeIsin     | age | monthlyWage | feeSaved
        [EE3600019832: 10000.0G] | [EE3600019832: 0.0075G, EE3600109435: 0.0055G] | "EE3600019832" | 30  | 1200        | 6629.86
        [EE3600019832: 10000.0G] | [EE3600019832: 0.0039G, EE3600109435: 0.0055G] | "EE3600019832" | 30  | 1200        | -5564.95
        [EE3600019832: 10000.0G] | [EE3600019832: 0.0092G, EE3600109435: 0.0055G] | "EE3600019832" | 30  | 1200        | 11993.91
    }

}
