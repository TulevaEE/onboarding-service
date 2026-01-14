package ee.tuleva.onboarding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@TestPropertySource(
    properties =
        "spring.config.import=optional:file:.env[.properties],optional:file:../.env[.properties]")
public @interface LoadDotEnv {}
