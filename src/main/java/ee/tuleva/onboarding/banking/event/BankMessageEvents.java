package ee.tuleva.onboarding.banking.event;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import java.time.LocalDate;
import java.util.UUID;

public final class BankMessageEvents {

  private BankMessageEvents() {}

  public record BankStatementReceived(UUID messageId, BankType bankType, BankStatement statement) {}

  public record SavingsFundStatementReceived(BankStatement statement, BankAccount account) {}

  public record FetchSebCurrentDayTransactionsRequested(BankAccount account) {}

  public record FetchSebEodTransactionsRequested(BankAccount account) {}

  public record FetchSebHistoricTransactionsRequested(
      BankAccount account, LocalDate dateFrom, LocalDate dateTo) {}

  public record ProcessBankMessagesRequested() {}

  public record BankMessagesProcessingCompleted() {}
}
