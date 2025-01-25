package ee.tuleva.onboarding


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest
@IgnoreIf({ System.getenv("CI") != "true" })
@ActiveProfiles("dev")
class OnboardingServicePostgresCISpec extends Specification {

  @Autowired
  WebApplicationContext context

  @Shared
  def postgres = new PostgreSQLContainer<>("postgres")
      .withDatabaseName("testdb")
      .withUsername("testuser")
      .withPassword("testpass")

  def setupSpec() {
    postgres.start()
    System.setProperty("spring.datasource.url", postgres.jdbcUrl)
    System.setProperty("spring.datasource.username", postgres.username)
    System.setProperty("spring.datasource.password", postgres.password)
  }

  def "context loads"() {
    expect:
    context != null
  }

  def cleanupSpec() {
    postgres.stop()
  }

}
