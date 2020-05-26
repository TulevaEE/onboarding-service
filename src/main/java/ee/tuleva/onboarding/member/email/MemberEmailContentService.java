package ee.tuleva.onboarding.member.email;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberEmailContentService {
    private final TemplateEngine templateEngine;

    public String getMembershipEmailHtml(User user, Locale locale) {
        DateTimeFormatter formatter =
            DateTimeFormatter.ISO_LOCAL_DATE
                .withZone(ZoneId.of("Europe/Tallinn"));
        Member member = user.getMemberOrThrow();
        String memberDate = formatter.format(member.getCreatedDate());

        Context ctx = new Context();
        ctx.setVariable("memberNumber", member.getMemberNumber());
        ctx.setVariable("firstName", user.getFirstName());
        ctx.setVariable("lastName", user.getLastName());
        ctx.setVariable("memberDate", memberDate);
        ctx.setLocale(locale);

        String htmlContent = templateEngine.process("membership", ctx);
        return htmlContent;
    }
}
