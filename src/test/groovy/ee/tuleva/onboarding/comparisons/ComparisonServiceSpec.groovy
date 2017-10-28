package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import java.math.RoundingMode

class ComparisonServiceSpec extends Specification {

	def userService = Mock(UserService)
	def accountStatementService = Mock(AccountStatementService)
	def fundReposirtory = Mock(FundRepository)

	def service = new ComparisonService(fundReposirtory, accountStatementService, userService)

	def cmd = new ComparisonCommand(
			isinTo: "tulevaIsin",
			age: 25,
			monthlyWage: 1000,
			currentCapitals: ["oldFundIsin": 5336.0],
			managementFeeRates: ["tulevaIsin": 0.0034])

	def "CalculateFVForSwitchPlan for non-tuleva-member"() {
		when:
		def futureValue = service.calculateFVForSwitchPlan(cmd)

		then:
		round(futureValue.withFee) == 159_706
		round(futureValue.withoutFee) == 173_572
	}

	def "CalculateFVForSwitchPlan for tuleva-member"() {
		given:
		cmd.isTulevaMember = true

		when:
		def futureValue = service.calculateFVForSwitchPlan(cmd)

		then:
		round(futureValue.withFee) == 161_659
		round(futureValue.withoutFee) == 173_572
	}


	private BigDecimal round(BigDecimal value) {
		return value.setScale(0, RoundingMode.HALF_UP);
	}
}
