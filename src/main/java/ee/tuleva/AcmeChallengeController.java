package ee.tuleva;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/aGEoUIi2k8n8wRaFf3-7d9ZyVmGxY9m6W_2I25G6g4M")
	@ResponseBody
	public String challenge() {
		return "aGEoUIi2k8n8wRaFf3-7d9ZyVmGxY9m6W_2I25G6g4M.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
