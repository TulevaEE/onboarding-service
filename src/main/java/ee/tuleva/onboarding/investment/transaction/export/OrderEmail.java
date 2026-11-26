package ee.tuleva.onboarding.investment.transaction.export;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

enum OrderEmail {
  FT(ExportFile.FT_ETF, Text.WRITTEN_HERE),
  SEB_ETF(ExportFile.SEB_ETF, Text.FROM_CONFIGURATION),
  SEB_FUND(ExportFile.SEB_FUND, Text.FROM_CONFIGURATION),
  UUID(ExportFile.UUID_WORKBOOK, Text.WRITTEN_HERE);

  private enum Text {
    WRITTEN_HERE,
    FROM_CONFIGURATION
  }

  private final ExportFile file;
  private final Text text;

  OrderEmail(ExportFile file, Text text) {
    this.file = file;
    this.text = text;
  }

  boolean takesItsTextFromConfiguration() {
    return text == Text.FROM_CONFIGURATION;
  }

  ExportFile file() {
    return file;
  }

  static List<OrderEmail> carrying(Map<ExportFile, byte[]> brokerFiles) {
    return Arrays.stream(values()).filter(email -> brokerFiles.containsKey(email.file)).toList();
  }
}
