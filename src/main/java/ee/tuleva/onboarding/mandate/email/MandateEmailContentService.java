package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MandateEmailContentService {

    private final TemplateEngine templateEngine;

    public String getSecondPillarHtml(User user, SecondPillarSuggestion pillarSuggestion, Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("pillarSuggestion", pillarSuggestion);
        return templateEngine.process("second_pillar_mandate", ctx);
    }

    public String getThirdPillarHtml(User user, ThirdPillarSuggestion pillarSuggestion, String pensionAccountNumber,
                                     Locale locale) {
        Context ctx = new Context();
        ctx.setLocale(locale);
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("pillarSuggestion", pillarSuggestion);
        ctx.setVariable("pensionAccountNumber", pensionAccountNumber);
        return templateEngine.process("third_pillar_mandate", ctx);
    }
}