package ee.tuleva;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static java.util.Collections.singletonMap;

@RestController
public class HelloWorldController {

	@RequestMapping("/")
	public Map<String, String> index() {
		return singletonMap("message", "Hello World");
	}


}
