package ee.tuleva.onboarding.config;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;

class TestSliceArchitectureTest {

  static JavaClasses testClasses;

  @BeforeAll
  static void importClasses() {
    testClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("ee.tuleva.onboarding");
  }

  @Test
  void noTestShouldUseDataJdbcTest() {
    noClasses()
        .should()
        .beAnnotatedWith(DataJdbcTest.class)
        .because("project uses JPA, not Spring Data JDBC — use @DataJpaTest or @JdbcTest instead")
        .check(testClasses);
  }
}
