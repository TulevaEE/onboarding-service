package ee.tuleva.onboarding.capital.transfer.content

import au.com.origin.snapshots.Expect
import au.com.origin.snapshots.annotations.SnapshotName
import au.com.origin.snapshots.spock.EnableSnapshots
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.member.Member
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import java.time.LocalDateTime

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.MEMBERSHIP_BONUS
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.CREATED
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture

@SpringBootTest
@EnableSnapshots
class CapitalTransferContractContentServiceTest extends Specification {

  @Autowired
  CapitalTransferContractContentService contentService

  private Expect expect

  @SnapshotName("capital_transfer_contract_member_capital")
  def "generates HTML for member capital transfer contract"() {
    given:
    User sellerUser = sampleUser()
        .firstName("John")
        .lastName("Seller")
        .personalCode("37605030299")
        .email("seller@example.com")
        .build()
    User buyerUser = sampleUser()
        .id(2L)
        .personalCode("60001019906")
        .firstName("Mari")
        .lastName("Buyer")
        .email("buyer@example.com")
        .build()

    Member seller = memberFixture().user(sellerUser).memberNumber(1001).build()
    Member buyer = memberFixture().user(buyerUser).memberNumber(1002).build()

    CapitalTransferContract contract = CapitalTransferContract.builder()
        .seller(seller)
        .buyer(buyer)
        .iban("EE471000001020145685")
        .transferAmounts(
            List.of(
                new CapitalTransferContract.CapitalTransferAmount(
                    CAPITAL_PAYMENT, new BigDecimal("2500.0"), new BigDecimal("1250.0"))))
        .state(CREATED)
        .createdAt(LocalDateTime.of(2023, 12, 15, 10, 30))
        .build()

    when:
    String html = contentService.generateContractHtml(contract)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("capital_transfer_contract_member_bonus")
  def "generates HTML for member bonus transfer contract"() {
    given:
    User sellerUser = sampleUser()
        .firstName("Jane")
        .lastName("Seller")
        .personalCode("48001010001")
        .email("jane@example.com")
        .build()
    User buyerUser = sampleUser()
        .id(2L)
        .personalCode("39001010002")
        .firstName("Peeter")
        .lastName("Buyer")
        .email("peeter@example.com")
        .build()

    Member seller = memberFixture().user(sellerUser).memberNumber(2001).build()
    Member buyer = memberFixture().user(buyerUser).memberNumber(2002).build()

    CapitalTransferContract contract = CapitalTransferContract.builder()
        .seller(seller)
        .buyer(buyer)
        .iban("EE211722839095030454")
        .transferAmounts(
            List.of(
                new CapitalTransferContract.CapitalTransferAmount(
                    MEMBERSHIP_BONUS, new BigDecimal("200.0"), new BigDecimal("100.0"))))
        .state(CREATED)
        .createdAt(LocalDateTime.of(2024, 1, 20, 14, 45))
        .build()

    when:
    String html = contentService.generateContractHtml(contract)

    then:
    expect.toMatchSnapshot(html)
  }

  @SnapshotName("capital_transfer_contract_multiple_amounts")
  def "generates HTML for multiple capital transfer amounts"() {
    given:
    User sellerUser = sampleUser()
        .firstName("Alice")
        .lastName("MultiSeller")
        .personalCode("50001010003")
        .email("alice@example.com")
        .build()
    User buyerUser = sampleUser()
        .id(2L)
        .personalCode("40001010004")
        .firstName("Bob")
        .lastName("MultiBuyer")
        .email("bob@example.com")
        .build()

    Member seller = memberFixture().user(sellerUser).memberNumber(3001).build()
    Member buyer = memberFixture().user(buyerUser).memberNumber(3002).build()

    CapitalTransferContract contract = CapitalTransferContract.builder()
        .seller(seller)
        .buyer(buyer)
        .iban("EE211722839095030454")
        .transferAmounts(
            List.of(
                new CapitalTransferContract.CapitalTransferAmount(
                    CAPITAL_PAYMENT, new BigDecimal("2500"), new BigDecimal("1250.0")),
                new CapitalTransferContract.CapitalTransferAmount(
                    MEMBERSHIP_BONUS, new BigDecimal("400"), new BigDecimal("200.0"))))
        .state(CREATED)
        .createdAt(LocalDateTime.of(2024, 3, 10, 16, 15))
        .build()

    when:
    String html = contentService.generateContractHtml(contract)

    then:
    expect.toMatchSnapshot(html)
  }

  def "generates contract content as bytes"() {
    given:
    User sellerUser = sampleUser().build()
    User buyerUser = sampleUser()
        .id(2L)
        .personalCode("60001019906")
        .firstName("Mari")
        .lastName("Buyer")
        .email("buyer@example.com")
        .build()

    Member seller = memberFixture().user(sellerUser).memberNumber(1001).build()
    Member buyer = memberFixture().user(buyerUser).memberNumber(1002).build()

    CapitalTransferContract contract = CapitalTransferContract.builder()
        .seller(seller)
        .buyer(buyer)
        .iban("EE211722839095030454")
        .transferAmounts(
            List.of(
                new CapitalTransferContract.CapitalTransferAmount(
                    CAPITAL_PAYMENT, new BigDecimal("100.0"), new BigDecimal("1250.0"))))
        .state(CREATED)
        .createdAt(LocalDateTime.of(2023, 12, 15, 10, 30))
        .build()

    when:
    byte[] result = contentService.generateContractContent(contract)

    then:
    result.length > 0
    new String(result).contains("Liikmekapitali võõrandamise avaldus")
  }
}
