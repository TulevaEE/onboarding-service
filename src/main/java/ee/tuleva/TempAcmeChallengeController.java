package ee.tuleva;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;

@Controller
public class TempAcmeChallengeController {

    @RequestMapping(value = "/.well-known/acme-challenge/a7XKHlcPF3EZdo0VYOMFxgB5gVmc1hkkP4qGuDHhJvI", produces = "text/plain")
    @ResponseBody
    public String challenge(HttpServletResponse response) {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        return "a7XKHlcPF3EZdo0VYOMFxgB5gVmc1hkkP4qGuDHhJvI.WmtYasmHj0-9Rw5MJjwC0wMmsYFHPXTJMhCCgqBxzTo";
    }

}
