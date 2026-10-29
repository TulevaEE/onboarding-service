package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SEB_FUND_CONFIRMATION_SAMPLES_DIR", matches = ".+")
class SebFundConfirmationPdfParserSamplesIT {

  private final SebFundConfirmationPdfParser parser =
      new SebFundConfirmationPdfParser(new SebClientNameToFundResolver());

  @Test
  void parsesEveryPdfInTheSamplesDirectory() throws IOException {
    File dir = new File(System.getenv("SEB_FUND_CONFIRMATION_SAMPLES_DIR"));
    File[] pdfs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
    assertThat(pdfs).as("PDFs in SEB_FUND_CONFIRMATION_SAMPLES_DIR=%s", dir).isNotEmpty();

    List<File> sorted = Arrays.stream(pdfs).sorted(Comparator.comparing(File::getName)).toList();

    for (File pdf : sorted) {
      SebFundConfirmation result = parser.parse(Files.readAllBytes(pdf.toPath()));
      System.out.printf("%s -> %s%n", pdf.getName(), result);
    }
  }
}
