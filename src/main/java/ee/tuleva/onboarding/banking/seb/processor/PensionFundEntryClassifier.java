package ee.tuleva.onboarding.banking.seb.processor;

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import java.math.BigDecimal;
import java.util.Optional;
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

  public record TradeSettlement(String isin, String ticker, String displayName, BigDecimal units)
      implements Classification {}

  public record Unclassified(String reason) implements Classification {}

  public Classification classify(BankStatementEntry entry) {
    var code = entry.subFamilyCode();
    if (code != null) {
      var byCode = classifyByCode(entry, code);
      if (byCode.isPresent()) {
        return byCode.get();
      }
    }
    return classifyByCounterparty(entry, code);
  }

  private Optional<Classification> classifyByCode(BankStatementEntry entry, String code) {
    switch (code) {
      case "INTR" -> {
        return Optional.of(new InterestReceived());
      }
      case "FEES", "COMM" -> {
        return Optional.of(new BankFee());
      }
      case "TRAD", "SUBS", "REDM" -> {
        return Optional.of(classifyTradeSettlement(entry));
      }
      default -> {}
    }
    if (isAdjustmentWithoutDetails(code, entry)) {
      return Optional.of(new BankAdjustment());
    }
    if (isKickbackBooking(code, entry)) {
      return Optional.of(new ManagementFeeRebate());
    }
    return Optional.empty();
  }

  private Classification classifyByCounterparty(BankStatementEntry entry, @Nullable String code) {
    var details = entry.details();
    if (details == null) {
      return new Unclassified("subFamilyCode=" + code);
    }
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

  private Classification classifyTradeSettlement(BankStatementEntry entry) {
    return tradeSettlementParser
        .parse(entry.remittanceInformation())
        .<Classification>map(
            info ->
                new TradeSettlement(info.isin(), info.ticker(), info.displayName(), info.units()))
        .orElseGet(() -> new Unclassified("unknown ticker"));
  }

  private static boolean isAdjustmentWithoutDetails(String code, BankStatementEntry entry) {
    return ("ADJT".equals(code) || "OTHR".equals(code)) && entry.details() == null;
  }

  private static boolean isKickbackBooking(String code, BankStatementEntry entry) {
    return "BOOK".equals(code) && isKickback(entry.remittanceInformation());
  }

  private static boolean isKickback(@Nullable String remittanceInformation) {
    return remittanceInformation != null
        && remittanceInformation.toLowerCase().contains("kickback");
  }
}
