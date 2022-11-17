package ee.tuleva.onboarding.test


import org.springframework.security.test.context.support.WithSecurityContext

import java.lang.annotation.*

@Target([ElementType.METHOD, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithPersonalCodeSecurityContextFactory.class)
@interface WithPersonalCodeUser {
  String value() default "38812121215";
}
