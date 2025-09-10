package ee.tuleva.onboarding.capital.transfer.content;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

class CapitalTransferContractContextBuilderTest {

  @Test
  void builder_createsNewInstance() {
    // when
    CapitalTransferContractContextBuilder builder = CapitalTransferContractContextBuilder.builder();

    // then
    assertThat(builder).isNotNull();
  }

  @Test
  void seller_setsSellerVariables() {
    // given
    User sellerUser =
        sampleUser().firstName("John").lastName("Seller").personalCode("37605030299").build();
    Member seller = memberFixture().user(sellerUser).memberNumber(1001).build();

    // when
    Context context = CapitalTransferContractContextBuilder.builder().seller(seller).build();

    // then
    assertThat(context.getVariable("seller")).isEqualTo(seller);
    assertThat(context.getVariable("sellerFirstName")).isEqualTo("John");
    assertThat(context.getVariable("sellerLastName")).isEqualTo("Seller");
    assertThat(context.getVariable("sellerPersonalCode")).isEqualTo("37605030299");
    assertThat(context.getVariable("sellerMemberNumber")).isEqualTo(1001);
  }

  @Test
  void buyer_setsBuyerVariables() {
    // given
    User buyerUser =
        sampleUser().firstName("Jane").lastName("Buyer").personalCode("60001019906").build();
    Member buyer = memberFixture().user(buyerUser).memberNumber(1002).build();

    // when
    Context context = CapitalTransferContractContextBuilder.builder().buyer(buyer).build();

    // then
    assertThat(context.getVariable("buyer")).isEqualTo(buyer);
    assertThat(context.getVariable("buyerFirstName")).isEqualTo("Jane");
    assertThat(context.getVariable("buyerLastName")).isEqualTo("Buyer");
    assertThat(context.getVariable("buyerPersonalCode")).isEqualTo("60001019906");
    assertThat(context.getVariable("buyerMemberNumber")).isEqualTo(1002);
  }

  @Test
  void iban_setsIbanVariable() {
    // given
    String iban = "EE471000001020145685";

    // when
    Context context = CapitalTransferContractContextBuilder.builder().iban(iban).build();

    // then
    assertThat(context.getVariable("iban")).isEqualTo(iban);
  }

  @Test
  void totalAmount_setsTotalAmountVariable() {
    // given
    BigDecimal totalAmount = new BigDecimal("1250.00");

    // when
    Context context =
        CapitalTransferContractContextBuilder.builder().totalAmount(totalAmount).build();

    // then
    assertThat(context.getVariable("totalAmount")).isEqualTo("1 250.00");
  }

  @Test
  void contractState_setsContractStateVariable() {
    // given
    CapitalTransferContractState contractState = CapitalTransferContractState.CREATED;

    // when
    Context context =
        CapitalTransferContractContextBuilder.builder().contractState(contractState).build();

    // then
    assertThat(context.getVariable("contractState")).isEqualTo(contractState);
  }

  @Test
  void createdAt_setsCreatedAtVariable() {
    // given
    LocalDateTime createdAt = LocalDateTime.of(2023, 12, 15, 10, 30);

    // when
    Context context = CapitalTransferContractContextBuilder.builder().createdAt(createdAt).build();

    // then
    assertThat(context.getVariable("createdAt")).isEqualTo(createdAt);
  }

  @Test
  void formattedCreatedAt_setsFormattedCreatedAtVariable() {
    // given
    String formattedCreatedAt = "15.12.2023 10:30";

    // when
    Context context =
        CapitalTransferContractContextBuilder.builder()
            .formattedCreatedAt(formattedCreatedAt)
            .build();

    // then
    assertThat(context.getVariable("formattedCreatedAt")).isEqualTo(formattedCreatedAt);
  }

  @Test
  void builder_supportsMethodChaining() {
    // given
    User sellerUser =
        sampleUser().firstName("John").lastName("Seller").personalCode("37605030299").build();
    Member seller = memberFixture().user(sellerUser).memberNumber(1001).build();

    User buyerUser =
        sampleUser().firstName("Jane").lastName("Buyer").personalCode("60001019906").build();
    Member buyer = memberFixture().user(buyerUser).memberNumber(1002).build();

    String iban = "EE471000001020145685";
    BigDecimal unitCount = BigDecimal.valueOf(100.0);
    BigDecimal unitsOfMemberBonus = BigDecimal.valueOf(2.0);
    BigDecimal totalAmount = new BigDecimal("1250.00");
    CapitalTransferContractState contractState = CapitalTransferContractState.CREATED;
    LocalDateTime createdAt = LocalDateTime.of(2023, 12, 15, 10, 30);
    String formattedCreatedAt = "15.12.2023 10:30";

    // when
    Context context =
        CapitalTransferContractContextBuilder.builder()
            .seller(seller)
            .buyer(buyer)
            .iban(iban)
            .totalAmount(totalAmount)
            .contractState(contractState)
            .createdAt(createdAt)
            .formattedCreatedAt(formattedCreatedAt)
            .build();

    // then
    assertThat(context.getVariable("seller")).isEqualTo(seller);
    assertThat(context.getVariable("sellerFirstName")).isEqualTo("John");
    assertThat(context.getVariable("sellerLastName")).isEqualTo("Seller");
    assertThat(context.getVariable("sellerPersonalCode")).isEqualTo("37605030299");
    assertThat(context.getVariable("sellerMemberNumber")).isEqualTo(1001);
    assertThat(context.getVariable("buyer")).isEqualTo(buyer);
    assertThat(context.getVariable("buyerFirstName")).isEqualTo("Jane");
    assertThat(context.getVariable("buyerLastName")).isEqualTo("Buyer");
    assertThat(context.getVariable("buyerPersonalCode")).isEqualTo("60001019906");
    assertThat(context.getVariable("buyerMemberNumber")).isEqualTo(1002);
    assertThat(context.getVariable("iban")).isEqualTo(iban);
    assertThat(context.getVariable("totalAmount")).isEqualTo("1 250.00");
    assertThat(context.getVariable("contractState")).isEqualTo(contractState);
    assertThat(context.getVariable("createdAt")).isEqualTo(createdAt);
    assertThat(context.getVariable("formattedCreatedAt")).isEqualTo(formattedCreatedAt);
  }

  @Test
  void build_returnsThymeleafContext() {
    // when
    Context context = CapitalTransferContractContextBuilder.builder().build();

    // then
    assertThat(context).isInstanceOf(Context.class);
  }
}
