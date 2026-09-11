package ee.tuleva.onboarding.savings.fund.notification;

import ee.tuleva.onboarding.banking.BankAccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record OwnAccountTransferRecordedEvent(
    BankAccountType fromAccount, BankAccountType toAccount, BigDecimal amount, UUID paymentId) {}
