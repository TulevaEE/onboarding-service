package ee.tuleva.onboarding.taxwin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class TaxWinPurposeLimitationTest {

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
          .importPackages("ee.tuleva.onboarding");

  @Test
  void taxDataConsentedForTheTaxWinIsReadByNothingElse() {
    noClasses()
        .that()
        .resideOutsideOfPackage("ee.tuleva.onboarding.taxwin..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("ee.tuleva.onboarding.taxwin..")
        .check(PRODUCTION_CLASSES);
  }
}
