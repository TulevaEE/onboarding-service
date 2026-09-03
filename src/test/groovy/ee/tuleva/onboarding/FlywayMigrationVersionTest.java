package ee.tuleva.onboarding;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionTest {

  private static final List<Path> FLYWAY_LOCATIONS =
      List.of(
          Path.of("src/main/resources/db/migration"),
          Path.of("src/main/resources/db/dev"),
          Path.of("src/main/java/db/migration"),
          Path.of("src/test/resources/db/h2"));

  @Test
  void everyMigrationVersionIsClaimedByExactlyOneFile() throws IOException {
    assertThat(duplicateVersions(versionedMigrationFileNames()))
        .as("Two migrations claiming one version stop Flyway from starting any schema")
        .isEmpty();
  }

  @Test
  void aVersionClaimedTwiceIsFoundEvenWhenTheFileNamesDiffer() {
    var fileNames =
        List.of(
            "V1_251__create_investment_account.sql",
            "V1_251__drop_legacy_benchmark_proxy_columns.sql",
            "V1_252__savings_fund_onboarding_updated_at.sql");

    assertThat(duplicateVersions(fileNames)).containsExactly("1.251");
  }

  @Test
  void underscoreAndDotSeparatorsNameTheSameVersion() {
    var fileNames = List.of("V1_249_1__h2_shim.sql", "V1_249.1__dev_seed.sql");

    assertThat(duplicateVersions(fileNames)).containsExactly("1.249.1");
  }

  @Test
  void javaAndSqlMigrationsShareOneVersionNamespace() {
    var fileNames =
        List.of("V1_250__reference_data_history_trigger.java", "V1_250__reference_data.sql");

    assertThat(duplicateVersions(fileNames)).containsExactly("1.250");
  }

  @Test
  void repeatableMigrationsCarryNoVersionAndAreLeftAlone() {
    var fileNames = List.of("R__refresh_views.sql", "R__rebuild_indexes.sql");

    assertThat(duplicateVersions(fileNames)).isEmpty();
  }

  private static List<String> duplicateVersions(List<String> fileNames) {
    return fileNames.stream()
        .filter(FlywayMigrationVersionTest::isVersioned)
        .collect(groupingBy(FlywayMigrationVersionTest::version, counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  private static boolean isVersioned(String fileName) {
    return fileName.startsWith("V") && fileName.contains("__");
  }

  private static String version(String fileName) {
    return fileName.substring(1, fileName.indexOf("__")).replace('_', '.');
  }

  private static List<String> versionedMigrationFileNames() throws IOException {
    var fileNames = new ArrayList<String>();
    for (Path location : FLYWAY_LOCATIONS) {
      if (!Files.isDirectory(location)) {
        continue;
      }
      try (Stream<Path> files = Files.list(location)) {
        files
            .filter(Files::isRegularFile)
            .map(file -> file.getFileName().toString())
            .forEach(fileNames::add);
      }
    }
    return List.copyOf(fileNames);
  }
}
