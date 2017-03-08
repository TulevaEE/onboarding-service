package ee.tuleva;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AcmeChallengeController {

	@RequestMapping(value = "/.well-known/acme-challenge/KmkeIleRyIRV7VQCFLK-L6EjRSwW_WZweA9DRQy21")
	@ResponseBody
	public String challenge() {
		return "KmkeIleRyIRV7VQCFLK-L6EjRSwW_WZweA9DRQy21-E.EMEBBxvSam3n_ien1J0z4dXeTuc2JuR3HqfAP6teLjE";
	}

}
