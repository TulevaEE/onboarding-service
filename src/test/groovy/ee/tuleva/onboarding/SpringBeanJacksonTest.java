package ee.tuleva.onboarding;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class SpringBeanJacksonTest {

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("ee.tuleva.onboarding");

  @Test
  void springBeansUseTheJackson3MapperThatBootProvides() {
    noClasses()
        .that()
        .areAnnotatedWith(Component.class)
        .or()
        .areAnnotatedWith(Service.class)
        .or()
        .areAnnotatedWith(Repository.class)
        .or()
        .areAnnotatedWith(Controller.class)
        .or()
        .areAnnotatedWith(RestController.class)
        .or()
        .areAnnotatedWith(ControllerAdvice.class)
        .or()
        .areAnnotatedWith(RestControllerAdvice.class)
        .or()
        .areAnnotatedWith(Configuration.class)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fasterxml.jackson.databind..")
        .because("Spring Boot 4 provides only the Jackson 3 tools.jackson mapper as a bean")
        .check(PRODUCTION_CLASSES);
  }
}
