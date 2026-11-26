package ee.tuleva.onboarding.investment.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;

final class SendRequestChecks {

  private SendRequestChecks() {}

  static void requireEachBatchOnce(List<ReviewedBatch> reviewedBatches) {
    long distinctBatches = reviewedBatches.stream().map(ReviewedBatch::id).distinct().count();
    if (distinctBatches != reviewedBatches.size()) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "A batch is listed more than once: ids="
              + reviewedBatches.stream().map(ReviewedBatch::id).toList());
    }
  }

  static void requireOneBatchPerFund(List<TransactionBatch> batches) {
    Map<TulevaFund, List<Long>> fundsSentTwice =
        batches.stream()
            .collect(
                groupingBy(TransactionBatch::getFund, mapping(TransactionBatch::getId, toList())))
            .entrySet()
            .stream()
            .filter(fund -> fund.getValue().size() > 1)
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (!fundsSentTwice.isEmpty()) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "A fund has more than one batch in this send, its orders would go out twice:"
              + " batchIdsByFund="
              + fundsSentTwice);
    }
  }
}
