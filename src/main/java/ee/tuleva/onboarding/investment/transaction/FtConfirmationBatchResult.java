package ee.tuleva.onboarding.investment.transaction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record FtConfirmationBatchResult(
    int index,
    @Nullable String isin,
    @Nullable FtConfirmationResult result,
    @Nullable String error) {

  public static FtConfirmationBatchResult verified(
      int index, String isin, FtConfirmationResult result) {
    return new FtConfirmationBatchResult(index, isin, result, null);
  }

  public static FtConfirmationBatchResult failed(int index, @Nullable String isin, String error) {
    return new FtConfirmationBatchResult(index, isin, null, error);
  }
}
