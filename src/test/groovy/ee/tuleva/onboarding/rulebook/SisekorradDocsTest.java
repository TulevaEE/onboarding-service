package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.sisekord.Sisekorrad.SISEKORD_13;
import static ee.tuleva.onboarding.sisekord.Sisekorrad.SISEKORD_4;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class SisekorradDocsTest {

  private static final List<Sisekord> SISEKORRAD = List.of(SISEKORD_4, SISEKORD_13);

  private static final Path DOCS = Path.of("docs", "sisekorrad");

  @TestFactory
  Stream<DynamicTest> everyExampleDecidesAsItIsLabelled() {
    return SISEKORRAD.stream()
        .flatMap(sisekord -> sisekord.rules().stream().flatMap(rule -> examples(sisekord, rule)));
  }

  private static Stream<DynamicTest> examples(Sisekord sisekord, Provision provision) {
    return provision.exampleNames().stream()
        .map(
            example ->
                dynamicTest(
                    "sisekord "
                        + sisekord.number()
                        + " p "
                        + provision.paragraph()
                        + ": "
                        + example,
                    () -> assertThat(provision.decidesAsLabelled(example)).isTrue()));
  }

  @TestFactory
  Stream<DynamicTest> theCheckedInRulebookIsTheOneTheCodeWrites() {
    return SISEKORRAD.stream()
        .map(sisekord -> dynamicTest(sisekord.fileName(), () -> rewriteWhenStale(sisekord)));
  }

  private static void rewriteWhenStale(Sisekord sisekord) throws IOException {
    var file = DOCS.resolve(sisekord.fileName());
    var written = sisekord.markdown();
    var checkedIn = Files.exists(file) ? Files.readString(file, UTF_8) : "";
    if (!checkedIn.equals(written)) {
      Files.createDirectories(DOCS);
      Files.writeString(file, written, UTF_8);
    }
    assertThat(checkedIn).isEqualTo(written);
  }
}
