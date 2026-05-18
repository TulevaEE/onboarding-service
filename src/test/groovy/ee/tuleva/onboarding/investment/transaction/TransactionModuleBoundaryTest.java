package ee.tuleva.onboarding.investment.transaction;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Boundary test for the {@code ee.tuleva.onboarding.investment.transaction} package, treated as a
 * Spring Modulith sub-module.
 *
 * <p>The intended public API of this module is the set of classes in the package root:
 *
 * <ul>
 *   <li>{@code TransactionOrder}, {@code TransactionOrderRepository}
 *   <li>{@code TransactionExecution}, {@code TransactionExecutionRepository}
 *   <li>{@code TransactionBatch}, {@code TransactionBatchRepository}
 *   <li>plus the package-root services and DTOs (enums, events) that already exist
 * </ul>
 *
 * <p>The sub-packages {@code ingest/}, {@code portfolio/}, {@code calculation/} and {@code export/}
 * are internal. No code outside {@code ee.tuleva.onboarding.investment.transaction..} may depend on
 * types declared in those sub-packages. Within the module they may freely reference each other and
 * the package-root types.
 *
 * <p>Note: Spring Modulith's {@code ApplicationModules.verify()} currently treats {@code
 * investment} (not {@code investment.transaction}) as the module, because no {@code package-info
 * .java} on {@code investment.transaction} carries {@code @ApplicationModule}. This test enforces
 * the intended sub-module boundary directly with ArchUnit until such an annotation is added.
 */
class TransactionModuleBoundaryTest {

  private static final String TRANSACTION_PACKAGE = "ee.tuleva.onboarding.investment.transaction";

  private static final String INGEST_PACKAGE = TRANSACTION_PACKAGE + ".ingest..";
  private static final String PORTFOLIO_PACKAGE = TRANSACTION_PACKAGE + ".portfolio..";
  private static final String CALCULATION_PACKAGE = TRANSACTION_PACKAGE + ".calculation..";
  private static final String EXPORT_PACKAGE = TRANSACTION_PACKAGE + ".export..";

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
          .importPackages("ee.tuleva.onboarding");

  @Test
  void publicApiClassesArePresentInPackageRoot() {
    assertThat(
            PRODUCTION_CLASSES.stream()
                .map(c -> c.getName())
                .filter(n -> n.startsWith(TRANSACTION_PACKAGE + "."))
                .filter(n -> !n.substring(TRANSACTION_PACKAGE.length() + 1).contains(".")))
        .as("public-API classes in investment.transaction package root")
        .contains(
            TRANSACTION_PACKAGE + ".TransactionOrder",
            TRANSACTION_PACKAGE + ".TransactionOrderRepository",
            TRANSACTION_PACKAGE + ".TransactionExecution",
            TRANSACTION_PACKAGE + ".TransactionExecutionRepository");
  }

  @Test
  void noCodeOutsideTransactionModuleDependsOnIngestPackage() {
    noClasses()
        .that()
        .resideOutsideOfPackages(TRANSACTION_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(INGEST_PACKAGE)
        .because(
            "ingest/ is internal to the investment.transaction module; expose domain types from the"
                + " package root instead")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void noCodeOutsideTransactionModuleDependsOnPortfolioPackage() {
    noClasses()
        .that()
        .resideOutsideOfPackages(TRANSACTION_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(PORTFOLIO_PACKAGE)
        .because(
            "portfolio/ is internal; the only public read API is"
                + " PortfolioCostBasisService.snapshotForFundAndDate(TulevaFund, LocalDate)"
                + " and even that should be promoted to the package root before external use")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void noCodeOutsideTransactionModuleDependsOnCalculationPackage() {
    noClasses()
        .that()
        .resideOutsideOfPackages(TRANSACTION_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CALCULATION_PACKAGE)
        .because("calculation/ is internal to the investment.transaction module")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  void noCodeOutsideTransactionModuleDependsOnExportPackage() {
    noClasses()
        .that()
        .resideOutsideOfPackages(TRANSACTION_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(EXPORT_PACKAGE)
        .because("export/ is internal to the investment.transaction module")
        .check(PRODUCTION_CLASSES);
  }
}
