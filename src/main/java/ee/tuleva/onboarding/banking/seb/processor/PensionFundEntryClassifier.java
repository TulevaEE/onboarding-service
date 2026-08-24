package ee.tuleva.onboarding.banking.seb.processor;

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@RequiredArgsConstructor
public class PensionFundEntryClassifier {

  private final TradeSettlementParser tradeSettlementParser;
  private final SebAccountConfiguration sebAccountConfiguration;

  public sealed interface Classification
      permits InterestReceived,
          BankFee,
          BankAdjustment,
          ManagementFeeRebate,
          ManagementFeePayment,
          RegistrarContribution,
          RegistrarPayout,
          OwnAccountTransfer,
          TradeSettlement,
          Unclassified {}

  public record InterestReceived() implements Classification {}

  public record BankFee() implements Classification {}

  public record BankAdjustment() implements Classification {}

  public record ManagementFeeRebate() implements Classification {}

  public record ManagementFeePayment() implements Classification {}

  public record RegistrarContribution() implements Classification {}

  public record RegistrarPayout() implements Classification {}

  public record OwnAccountTransfer() implements Classification {}

  public record TradeSettlement(FundTicker ticker, BigDecimal units) implements Classification {}

  public record Unclassified(String reason) implements Classification {}

  public Classification classify(BankStatementEntry entry) {
    var code = entry.subFamilyCode();
    if (code != null) {
      switch (code) {
        case "INTR" -> {
          return new InterestReceived();
        }
        case "FEES", "COMM" -> {
          return new BankFee();
        }
        case "TRAD", "SUBS", "REDM" -> {
          return classifyTradeSettlement(entry);
        }
        default -> {}
      }
      if (("ADJT".equals(code) || "OTHR".equals(code)) && entry.details() == null) {
        return new BankAdjustment();
      }
      if ("BOOK".equals(code) && isKickback(entry.remittanceInformation())) {
        return new ManagementFeeRebate();
      }
    }

    var details = entry.details();
    if (details != null) {
      var name = details.getName();
      if (name != null && sebAccountConfiguration.isManagementCompany(name)) {
        return entry.amount().signum() < 0 ? new ManagementFeePayment() : new ManagementFeeRebate();
      }
      if (sebAccountConfiguration.getRegistrarIbans().contains(details.getIban())) {
        return entry.amount().signum() > 0 ? new RegistrarContribution() : new RegistrarPayout();
      }
      if (sebAccountConfiguration.getOwnAccountIbans().contains(details.getIban())) {
        return new OwnAccountTransfer();
      }
      if (sebAccountConfiguration.getBankFeeIbans().contains(details.getIban())) {
        return new BankFee();
      }
      return new Unclassified("unknown counterparty");
    }

    return new Unclassified("subFamilyCode=" + code);
  }

  private Classification classifyTradeSettlement(BankStatementEntry entry) {
    return tradeSettlementParser
        .parse(entry.remittanceInformation())
        .<Classification>map(info -> new TradeSettlement(info.ticker(), info.units()))
        .orElseGet(() -> new Unclassified("unknown ticker"));
  }

  private static boolean isKickback(@Nullable String remittanceInformation) {
    return remittanceInformation != null
        && remittanceInformation.toLowerCase().contains("kickback");
  }
}
