package ee.tuleva.onboarding.capital.transfer.content;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState;
import ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    ctx.setVariable("sellerFullName", seller.getFullName());
    ctx.setVariable("sellerPersonalCode", seller.getUser().getPersonalCode());
    ctx.setVariable("sellerMemberNumber", seller.getMemberNumber());
    return this;
  }

  public CapitalTransferContractContextBuilder buyer(Member buyer) {
    ctx.setVariable("buyer", buyer);
    ctx.setVariable("buyerFullName", buyer.getFullName());
    ctx.setVariable("buyerPersonalCode", buyer.getUser().getPersonalCode());
    ctx.setVariable("buyerMemberNumber", buyer.getMemberNumber());
    return this;
  }

  public CapitalTransferContractContextBuilder iban(String iban) {
    ctx.setVariable("iban", iban);
    try {
      BankAccountDetails.Bank bank = BankAccountDetails.Bank.fromIban(iban);
      ctx.setVariable("bankName", bank.getDisplayName());
    } catch (IllegalArgumentException e) {
      ctx.setVariable("bankName", "");
    }
    return this;
  }

  public CapitalTransferContractContextBuilder totalAmount(BigDecimal totalAmount) {
    ctx.setVariable("totalAmount", formatEstonianCurrency(totalAmount));
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

  public CapitalTransferContractContextBuilder transferAmounts(
      List<CapitalTransferContract.CapitalTransferAmount> transferAmounts) {
    List<TransferAmountView> formattedTransferAmounts =
        transferAmounts.stream().map(this::createTransferAmountView).collect(toList());

    ctx.setVariable("transferAmounts", formattedTransferAmounts);

    BigDecimal totalAmount =
        transferAmounts.stream()
            .map(CapitalTransferContract.CapitalTransferAmount::bookValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ctx.setVariable("totalAmount", formatEstonianCurrency(totalAmount));

    BigDecimal totalPrice =
        transferAmounts.stream()
            .map(CapitalTransferContract.CapitalTransferAmount::price)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ctx.setVariable("totalPrice", formatEstonianCurrency(totalPrice));

    return this;
  }

  private TransferAmountView createTransferAmountView(
      CapitalTransferContract.CapitalTransferAmount transferAmount) {
    return new TransferAmountView(
        getShareTypeName(transferAmount.type()),
        formatEstonianCurrency(transferAmount.bookValue()));
  }

  public static class TransferAmountView {
    private final String typeName;
    private final String formattedBookValue;

    public TransferAmountView(String typeName, String formattedBookValue) {
      this.typeName = typeName;
      this.formattedBookValue = formattedBookValue;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getFormattedBookValue() {
      return formattedBookValue;
    }
  }

  private String getShareTypeName(MemberCapitalEventType type) {
    return switch (type) {
      case CAPITAL_PAYMENT -> "Rahaline panus";
      case MEMBERSHIP_BONUS -> "Liikmeboonus";
      case WORK_COMPENSATION -> "Tööpanus";
      case UNVESTED_WORK_COMPENSATION -> "Broneeritud tööpanus";
      case INVESTMENT_RETURN -> "Investeeringutulu";
      case CAPITAL_ACQUIRED -> "Omandatud liikmekapital";
    };
  }

  private String formatEstonianCurrency(BigDecimal amount) {
    if (amount == null) {
      return "0.00";
    }
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("et-EE"));
    symbols.setDecimalSeparator('.');
    symbols.setGroupingSeparator(' ');

    DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
    return formatter.format(amount);
  }
}
