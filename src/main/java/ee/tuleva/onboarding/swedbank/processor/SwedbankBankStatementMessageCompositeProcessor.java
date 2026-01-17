package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;

import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.swedbank.reconcillation.SwedbankCheckingReconciliator;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SwedbankBankStatementMessageCompositeProcessor implements SwedbankMessageProcessor {

  private final SwedbankBankStatementMessageProcessor swedbankBankStatementMessageProcessor;
  private final SwedbankCheckingReconciliator swedbankCheckingReconciliator;

  @Override
  @Transactional
  public void processMessage(String rawResponse, BankMessageType messageType, ZoneId timezone) {
    this.swedbankBankStatementMessageProcessor.processMessage(rawResponse, messageType, timezone);

    if (messageType == HISTORIC_STATEMENT) {
      this.swedbankCheckingReconciliator.processMessage(rawResponse, timezone);
    }
  }

  @Override
  public boolean supports(BankMessageType messageType) {
    return messageType == INTRA_DAY_REPORT || messageType == HISTORIC_STATEMENT;
  }
}
