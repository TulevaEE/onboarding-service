package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FT_SAMPLES_DIR", matches = ".+")
class FtConfirmationPdfParserSamplesIT {

  private final FtConfirmationPdfParser parser =
      new FtConfirmationPdfParser(new FtAccountToFundResolver());

  @Test
  void parsesEveryPdfInTheSamplesDirectory() throws IOException {
    File dir = new File(System.getenv("FT_SAMPLES_DIR"));
    File[] pdfs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
    assertThat(pdfs).as("PDFs in FT_SAMPLES_DIR=%s", dir).isNotEmpty();

    List<File> sorted = Arrays.stream(pdfs).sorted(Comparator.comparing(File::getName)).toList();

    for (File pdf : sorted) {
      byte[] bytes = Files.readAllBytes(pdf.toPath());
      FtConfirmation result = parser.parse(bytes);
      System.out.printf(
          "%s -> fund=%s, isin=%s, tradeDate=%s, quantity=%s, grossPrice=%s, type=%s, account=%s%n",
          pdf.getName(),
          result.fund(),
          result.isin(),
          result.tradeDate(),
          result.quantity(),
          result.grossPrice(),
          result.type(),
          result.account());
    }
  }
}
