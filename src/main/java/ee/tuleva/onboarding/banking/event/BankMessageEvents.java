package ee.tuleva.onboarding.banking.event;

import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import java.util.UUID;

public final class BankMessageEvents {

  private BankMessageEvents() {}

  public record BankStatementReceived(UUID messageId, BankType bankType, BankStatement statement) {}
}
