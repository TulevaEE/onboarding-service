package ee.tuleva.onboarding.mandate.content

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER

@SpringBootTest
@EnableSnapshots
class MandateContentServiceSpec extends Specification {

  @Autowired
  MandateContentService mandateContentService

  private Expect expect

  @SnapshotName("fund_transfer_2nd_pillar")
  def "fund transfer mandate for 2nd pillar"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    def funds = sampleFunds()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchanges = sampleMandate().fundTransferExchangesBySourceIsin["EE3600019790"]

    when:
    String html = mandateContentService.getFundTransferHtml(fundTransferExchanges, user, mandate, funds, contactDetails)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("fund_transfer_3rd_pillar")
  def "fund transfer mandate for 3rd pillar"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = thirdPillarMandate()
    def funds = sampleFunds()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchanges = sampleMandate().fundTransferExchangesBySourceIsin["EE3600019790"]

    when:
    String html = mandateContentService.getFundTransferHtml(fundTransferExchanges, user, mandate, funds, contactDetails)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("future_contributions_2nd_pillar")
  def "future contributions mandate for 2nd pillar"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    def funds = sampleFunds()
    def contactDetails = contactDetailsFixture()

    when:
    String html = mandateContentService.getFutureContributionsFundHtml(user, mandate, funds, contactDetails)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("future_contributions_3rd_pillar")
  def "future contributions mandate for 3rd pillar"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = thirdPillarMandate()
    def funds = sampleFunds()
    def contactDetails = contactDetailsFixture()

    when:
    String html = mandateContentService.getFutureContributionsFundHtml(user, mandate, funds, contactDetails)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("mandate_cancellation")
  def "mandate cancellation"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    def contactDetails = contactDetailsFixture()
    def applicationType = TRANSFER

    when:
    String html = mandateContentService.getMandateCancellationHtml(user, mandate, contactDetails, applicationType)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("payment_rate_change")
  def "payment rate change"() {
    given:
    def user = sampleUserNonMember().build()
    def mandate = sampleMandate()
    def contactDetails = contactDetailsFixture()

    when:
    String html = mandateContentService.getRateChangeHtml(user, mandate, contactDetails, new BigDecimal(6))

    then:
    expect.toMatchSnapshot(html)
  }

}
