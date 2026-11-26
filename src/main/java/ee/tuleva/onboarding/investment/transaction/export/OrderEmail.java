package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent.TRADE_ORDER_TAG;
import static java.util.Locale.ROOT;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

enum OrderEmail {
  FT(ExportFile.FT_ETF),
  SEB_ETF(ExportFile.SEB_ETF),
  SEB_FUND(ExportFile.SEB_FUND),
  UUID(ExportFile.UUID_WORKBOOK);

  private final ExportFile file;

  OrderEmail(ExportFile file) {
    this.file = file;
  }

  ExportFile file() {
    return file;
  }

  boolean takesItsTextFromConfiguration() {
    return file == ExportFile.SEB_ETF || file == ExportFile.SEB_FUND;
  }

  String tag() {
    return TRADE_ORDER_TAG + "-" + file.name().toLowerCase(ROOT).replace('_', '-');
  }

  boolean carries(TransactionOrder order) {
    return file.takes(order);
  }

  static List<OrderEmail> carrying(Map<ExportFile, byte[]> brokerFiles) {
    return Arrays.stream(values()).filter(email -> brokerFiles.containsKey(email.file)).toList();
  }

  static Optional<OrderEmail> taggedIn(List<String> tags) {
    return Arrays.stream(values()).filter(email -> tags.contains(email.tag())).findFirst();
  }
}
