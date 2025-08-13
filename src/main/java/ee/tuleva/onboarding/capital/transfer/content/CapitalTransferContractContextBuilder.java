package ee.tuleva.onboarding.capital.transfer.content;

import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.thymeleaf.context.Context;

public class CapitalTransferContractContextBuilder {

  private Context ctx = new Context();

  public static CapitalTransferContractContextBuilder builder() {
    return new CapitalTransferContractContextBuilder();
  }

  public Context build() {
    return ctx;
  }

  public CapitalTransferContractContextBuilder seller(Member seller) {
    ctx.setVariable("seller", seller);
    ctx.setVariable("sellerFirstName", seller.getUser().getFirstName());
    ctx.setVariable("sellerLastName", seller.getUser().getLastName());
    ctx.setVariable("sellerPersonalCode", seller.getUser().getPersonalCode());
    ctx.setVariable("sellerMemberNumber", seller.getMemberNumber());
    return this;
  }

  public CapitalTransferContractContextBuilder buyer(Member buyer) {
    ctx.setVariable("buyer", buyer);
    ctx.setVariable("buyerFirstName", buyer.getUser().getFirstName());
    ctx.setVariable("buyerLastName", buyer.getUser().getLastName());
    ctx.setVariable("buyerPersonalCode", buyer.getUser().getPersonalCode());
    ctx.setVariable("buyerMemberNumber", buyer.getMemberNumber());
    return this;
  }

  public CapitalTransferContractContextBuilder iban(String iban) {
    ctx.setVariable("iban", iban);
    return this;
  }

  public CapitalTransferContractContextBuilder unitCount(BigDecimal unitCount) {
    ctx.setVariable("unitCount", unitCount);
    return this;
  }

  public CapitalTransferContractContextBuilder unitsOfMemberBonus(BigDecimal unitsOfMemberBonus) {
    ctx.setVariable("unitsOfMemberBonus", unitsOfMemberBonus);
    return this;
  }

  public CapitalTransferContractContextBuilder totalAmount(BigDecimal totalAmount) {
    ctx.setVariable("totalAmount", totalAmount);
    return this;
  }

  public CapitalTransferContractContextBuilder contractState(
      CapitalTransferContractState contractState) {
    ctx.setVariable("contractState", contractState);
    return this;
  }

  public CapitalTransferContractContextBuilder createdAt(LocalDateTime createdAt) {
    ctx.setVariable("createdAt", createdAt);
    return this;
  }

  public CapitalTransferContractContextBuilder formattedCreatedAt(String formattedCreatedAt) {
    ctx.setVariable("formattedCreatedAt", formattedCreatedAt);
    return this;
  }
}
