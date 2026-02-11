package ee.tuleva.onboarding.banking.event;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import java.time.LocalDate;
import java.util.UUID;

public final class BankMessageEvents {

  private BankMessageEvents() {}

  public record BankStatementReceived(UUID messageId, BankType bankType, BankStatement statement) {}

  public record FetchSebCurrentDayTransactionsRequested(BankAccountType accountType) {}

  public record FetchSebEodTransactionsRequested(BankAccountType accountType) {}

  public record FetchSebHistoricTransactionsRequested(
      BankAccountType accountType, LocalDate dateFrom, LocalDate dateTo) {}

  public record ProcessBankMessagesRequested() {}

  public record BankMessagesProcessingCompleted() {}
}
