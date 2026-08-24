package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FundTickerConsumerFreezeTest {

  private static final String RETRIEVAL_PACKAGE =
      "ee.tuleva.onboarding.comparisons.fundvalue.retrieval";

  private static final String FUND_TICKER = RETRIEVAL_PACKAGE + ".FundTicker";

  // Frozen inventory: FundTicker is being retired in favour of the database-backed
  // instrument_reference table. This list may only ever shrink, and dies with the enum.
  // The second group reaches FundTicker through records returned by the first group, so it
  // carries no import of the enum and does not show up in a text search for it.
  private static final Set<String> CONSUMERS_AWAITING_INSTRUMENT_REFERENCE_CUTOVER =
      Set.of(
          "ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver",
          "ee.tuleva.onboarding.comparisons.fundvalue.PriceDataFreshnessAlertJob",
          "ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider",
          "ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityChecker",
          "ee.tuleva.onboarding.banking.processor.TradeSettlementParser",
          "ee.tuleva.onboarding.banking.seb.processor.PensionFundEntryClassifier",
          "ee.tuleva.onboarding.savings.fund.nav.NavReportMapper",
          "ee.tuleva.onboarding.investment.check.tracking.BenchmarkLegResolver",
          "ee.tuleva.onboarding.banking.processor.BankOperationProcessor",
          "ee.tuleva.onboarding.banking.seb.processor.PensionFundStatementProcessor",
          "ee.tuleva.onboarding.investment.check.tracking.PeriodicTdAttributionService");

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
          .importPackages("ee.tuleva.onboarding");

  @Test
  void productionClassesAreActuallyImported() {
    assertThat(PRODUCTION_CLASSES.stream().map(JavaClass::getName))
        .hasSizeGreaterThan(500)
        .contains(FUND_TICKER);
  }

  @Test
  void noNewClassDependsOnFundTicker() {
    noClasses()
        .that()
        .resideOutsideOfPackage(RETRIEVAL_PACKAGE)
        .and(not(isFrozenConsumer()))
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(FUND_TICKER)
        .because(
            "FundTicker is being retired: use InstrumentReferenceService instead of adding a new"
                + " consumer of the enum")
        .check(PRODUCTION_CLASSES);
  }

  private static DescribedPredicate<JavaClass> isFrozenConsumer() {
    return new DescribedPredicate<>("a frozen FundTicker consumer") {
      @Override
      public boolean test(JavaClass javaClass) {
        return CONSUMERS_AWAITING_INSTRUMENT_REFERENCE_CUTOVER.contains(
            topLevelName(javaClass.getName()));
      }
    };
  }

  private static String topLevelName(String className) {
    var nestedSeparator = className.indexOf('$');
    return nestedSeparator < 0 ? className : className.substring(0, nestedSeparator);
  }
}
