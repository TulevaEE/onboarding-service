package ee.tuleva.onboarding.accounting;

import static ee.tuleva.onboarding.accounting.directo.DirectoPartMapper.SOURCE;

import ee.tuleva.onboarding.accounting.directo.DirectoClient;
import ee.tuleva.onboarding.accounting.directo.DirectoPartMapper;
import ee.tuleva.onboarding.ledger.GeneralLedger;
import ee.tuleva.onboarding.ledger.MirrorResult;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralLedgerSync {

  private final DirectoClient directoClient;
  private final DirectoPartMapper partMapper;
  private final GeneralLedger generalLedger;

  @Value("${accounting.max-deletion-share}")
  private final double maxDeletionShare;

  public List<String> entities() {
    return directoClient.entities();
  }

  public MirrorResult sync(String entity) {
    var book = partMapper.map(directoClient.fetchBook(entity));
    logPartsProtectedAfterPosting(entity, book.protectedSourceKeys());
    generalLedger.upsertAccounts(entity, book.accounts());
    var result = generalLedger.mirror(entity, SOURCE, book.parts(), maxDeletionShare);
    log.info(
        "General ledger synced: entity={}, payrollProtectedParts={}, posted={}, revised={},"
            + " reversed={}, unchanged={}, quarantined={}, quarantinedWithLiveVersion={}",
        entity,
        book.protectedSourceKeys().size(),
        result.posted(),
        result.revised(),
        result.reversed(),
        result.unchanged(),
        result.quarantined(),
        result.quarantinedWithLiveVersion());
    return result;
  }

  private void logPartsProtectedAfterPosting(String entity, Set<String> protectedSourceKeys) {
    var protectedAfterPosting =
        generalLedger.liveSourceKeys(entity, SOURCE).stream()
            .filter(protectedSourceKeys::contains)
            .sorted()
            .toList();
    if (!protectedAfterPosting.isEmpty()) {
      log.error(
          "General ledger parts became payroll-protected after they were posted, so each is"
              + " reversed once its monthly summary posts and its earlier revision stays in the"
              + " ledger: entity={}, count={}, sourceKeySample={}",
          entity,
          protectedAfterPosting.size(),
          protectedAfterPosting.stream().limit(10).toList());
    }
  }
}
