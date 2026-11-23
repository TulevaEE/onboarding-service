package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebFundConfirmationPricingDateKeeper {

  private final SebFundConfirmationRecorder confirmationRecorder;
  private final SebFundConfirmationNavDateWriter navDateWriter;

  Optional<SebFundConfirmationCheckFailure> keep(SebFundConfirmationUpload upload) {
    String reference = upload.tradeReference();
    try {
      confirmationRecorder.record(upload);
      navDateWriter.write(reference);
    } catch (OptimisticLockingFailureException e) {
      log.info(
          "SEB fund confirmation pricing date left for the next run, execution changed meanwhile:"
              + " tradeReference={}",
          reference);
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation pricing date not written: tradeReference={}, error={}",
          reference,
          e.getMessage(),
          e);
      return Optional.of(
          new SebFundConfirmationCheckFailure(
              "tehingu " + reference + " hinnapäeva salvestamine",
              Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName())));
    }
    return Optional.empty();
  }
}
