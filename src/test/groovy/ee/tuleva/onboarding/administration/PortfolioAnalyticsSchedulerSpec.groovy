package ee.tuleva.onboarding.administration

import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.ZoneId

class PortfolioAnalyticsSchedulerSpec extends Specification {
  def portfolioAnalyticsSource
  def portfolioCsvProcessor
  def scheduler
  def clock = TestClockHolder.clock

  def setup() {
    portfolioAnalyticsSource = Mock(PortfolioAnalyticsSource)
    portfolioCsvProcessor = Mock(PortfolioCsvProcessor)
    scheduler = new PortfolioAnalyticsScheduler(portfolioAnalyticsSource, portfolioCsvProcessor, clock)
  }

  def "test fetchPortfolioAnalyticsCsv when CSV is available for both dates"() {
    given: "Specific dates and CSVs are available"
        def mockInputStreamToday = new ByteArrayInputStream("csvDataToday".getBytes())
        def mockInputStreamYesterday = new ByteArrayInputStream("csvDataYesterday".getBytes())
        def currentDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        def yesterdayDate = currentDate.minusDays(1)

    when: "fetchPortfolioAnalytics is called"
        scheduler.fetchPortfolioAnalytics()

    then: "fetchCsv is called on the source for both dates"
        1 * portfolioAnalyticsSource.fetchCsv(currentDate) >> Optional.of(mockInputStreamToday)
        1 * portfolioAnalyticsSource.fetchCsv(yesterdayDate) >> Optional.of(mockInputStreamYesterday)

    and: "process is called because CSVs are available for both dates"
        1 * portfolioCsvProcessor.process(currentDate, mockInputStreamToday)
        1 * portfolioCsvProcessor.process(yesterdayDate, mockInputStreamYesterday)
  }

  def "test fetchPortfolioAnalyticsCsv when CSV is not available for both dates"() {
    given: "Specific dates and no CSVs are available"
        def currentDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        def yesterdayDate = currentDate.minusDays(1)

    when: "fetchPortfolioAnalytics is called"
        scheduler.fetchPortfolioAnalytics()

    then: "fetchCsv is called on the source for both dates"
        1 * portfolioAnalyticsSource.fetchCsv(currentDate) >> Optional.empty()
        1 * portfolioAnalyticsSource.fetchCsv(yesterdayDate) >> Optional.empty()

    and: "process is not called because no CSVs are available"
        0 * portfolioCsvProcessor.process(_, _)
  }
}
