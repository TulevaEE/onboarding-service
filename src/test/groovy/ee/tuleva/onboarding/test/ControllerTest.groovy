package ee.tuleva.onboarding.test

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.auth.PersonalCodeAuthenticationProvider
import ee.tuleva.onboarding.error.converter.ErrorAttributesConverter
import ee.tuleva.onboarding.error.converter.InputErrorsConverter
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import([ErrorResponseEntityFactory, InputErrorsConverter, ErrorAttributesConverter, ConversionDecorator])
@MockBean(value = [FundRepository, CashFlowService, AccountStatementService])
@interface ControllerTest {
  @AliasFor(value = "value", annotation = WebMvcTest)
  Class<?>[] controllers() default [];
}
