package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.swedbank.processor.SwedbankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.processor.SwedbankMessageType.INTRA_DAY_REPORT;

import ee.tuleva.onboarding.swedbank.reconcillation.SwedbankCheckingReconciliator;
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
  public void processMessage(String rawResponse, SwedbankMessageType messageType) {
    this.swedbankBankStatementMessageProcessor.processMessage(rawResponse, messageType);

    if (messageType == HISTORIC_STATEMENT) {
      this.swedbankCheckingReconciliator.processMessage(rawResponse);
    }
  }

  @Override
  public boolean supports(SwedbankMessageType messageType) {
    return messageType == INTRA_DAY_REPORT || messageType == HISTORIC_STATEMENT;
  }
}
