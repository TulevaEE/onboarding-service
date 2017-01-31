package ee.tuleva;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;

@Controller
public class TempAcmeChallengeController {

    @RequestMapping(value = "/.well-known/acme-challenge/8cOWlAHexK7H8RE-gmXWByhZkZzhFuAS0XzjzcH3obk", produces = "text/plain")
    @ResponseBody
    public String challenge(HttpServletResponse response) {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        return "8cOWlAHexK7H8RE-gmXWByhZkZzhFuAS0XzjzcH3obk.WmtYasmHj0-9Rw5MJjwC0wMmsYFHPXTJMhCCgqBxzTo";
    }

}
