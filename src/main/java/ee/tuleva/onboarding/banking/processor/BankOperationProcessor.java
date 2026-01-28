package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankOperationProcessor {

  private static final String FEES = "FEES";
  private static final String INTR = "INTR";
  private static final String ADJT = "ADJT";

  public void processBankOperation(BankStatementEntry entry, UUID messageId) {
    if (entry.details() != null) {
      return;
    }

    var subFamilyCode = entry.subFamilyCode();
    if (subFamilyCode == null) {
      log.warn(
          "Bank operation without SubFmlyCd: externalId={}, amount={}",
          entry.externalId(),
          entry.amount());
      return;
    }

    var externalReference =
        UUID.nameUUIDFromBytes((messageId + ":" + entry.externalId()).getBytes());

    switch (subFamilyCode) {
      case INTR ->
          log.info(
              "Bank interest received: amount={}, externalRef={}, description={}",
              entry.amount(),
              externalReference,
              entry.remittanceInformation());
      case FEES ->
          log.info(
              "Bank fee charged: amount={}, externalRef={}, description={}",
              entry.amount(),
              externalReference,
              entry.remittanceInformation());
      case ADJT ->
          log.info(
              "Bank fee adjustment: amount={}, externalRef={}, description={}",
              entry.amount(),
              externalReference,
              entry.remittanceInformation());
      default ->
          log.warn(
              "Unknown bank operation SubFmlyCd: subFamilyCode={}, externalId={}, amount={}",
              subFamilyCode,
              entry.externalId(),
              entry.amount());
    }
  }
}
