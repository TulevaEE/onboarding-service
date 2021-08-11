package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static java.math.BigDecimal.ONE
import static java.math.BigDecimal.ZERO

class AccountStatementServiceSpec extends Specification {

  def episService = Mock(EpisService)
  def fundBalanceConverter = Mock(FundBalanceDtoToFundBalanceConverter)

  def service = new AccountStatementService(episService, fundBalanceConverter)

  def "returns an account statement"() {
    given:
    def person = samplePerson()
    def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
    def fundBalance = activeTuleva2ndPillarFundBalance.first()

    episService.getAccountStatement(person) >> [fundBalanceDto]
    fundBalanceConverter.convert(fundBalanceDto, person) >> fundBalance

    when:
    List<FundBalance> accountStatement = service.getAccountStatement(person)

    then:
    accountStatement == [fundBalance]
  }

  def "fundBalanceDto with no Isin code are filtered out and will not try to convert"() {
    given:
    def person = samplePerson()
    def fundBalanceDto = FundBalanceDto.builder().isin(null).build()
    episService.getAccountStatement(person) >> [fundBalanceDto]

    when:
    List<FundBalance> accountStatement = service.getAccountStatement(person)

    then:
    accountStatement.isEmpty()
    0 * fundBalanceConverter.convert(fundBalanceDto, person)
  }

  def "filters out non-active zero balance second pillar funds"() {
    given:
    def person = samplePerson()
    def inactiveZeroFund = FundBalanceDto.builder().isin("1").value(ZERO).activeContributions(false).pillar(2).build()
    def inactiveNonZeroFund = FundBalanceDto.builder().isin("2").value(ONE).activeContributions(false).pillar(2).build()
    def activeZeroFund = FundBalanceDto.builder().isin("3").value(ZERO).activeContributions(true).pillar(2).build()
    def activeNonZeroFund = FundBalanceDto.builder().isin("4").value(ONE).activeContributions(true).pillar(2).build()

    episService.getAccountStatement(person) >> [inactiveZeroFund, inactiveNonZeroFund, activeZeroFund, activeNonZeroFund]
    fundBalanceConverter.convert(_, _) >> { FundBalanceDto fundBalanceDto, _ ->
      FundBalance.builder().fund(Fund.builder().isin(fundBalanceDto.isin).build()).build()
    }

    when:
    List<FundBalance> accountStatement = service.getAccountStatement(person)

    then:
    accountStatement.size() == 3
    with(accountStatement.get(0)) { isin == inactiveNonZeroFund.isin }
    with(accountStatement.get(1)) { isin == activeZeroFund.isin }
    with(accountStatement.get(2)) { isin == activeNonZeroFund.isin }
  }

  def "does not filter out zero balance third pillar funds"() {
    given:
    def person = samplePerson()
    def inactiveZeroFund = FundBalanceDto.builder().isin("1").value(ZERO).activeContributions(false).pillar(3).build()

    episService.getAccountStatement(person) >> [inactiveZeroFund]
    fundBalanceConverter.convert(_, _) >> { FundBalanceDto fundBalanceDto, _ ->
      FundBalance.builder().fund(Fund.builder().isin(fundBalanceDto.isin).build()).build()
    }

    when:
    List<FundBalance> accountStatement = service.getAccountStatement(person)

    then:
    with(accountStatement.get(0)) { isin == inactiveZeroFund.isin }
  }

  def "handles fundBalanceDto to fundBalance conversion exceptions"() {
    given:
    def person = samplePerson()
    def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()

    episService.getAccountStatement(person) >> [fundBalanceDto]
    fundBalanceConverter.convert(fundBalanceDto, person) >> {
      throw new IllegalArgumentException()
    }

    when:
    service.getAccountStatement(person)

    then:
    thrown(IllegalStateException)
  }
}
