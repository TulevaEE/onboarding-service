package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/*
Temporary class to get Mandate XML message
*/


@RequiredArgsConstructor
@Slf4j
@Service
public class MandateXmlService {

    private final MandateService mandateService;

    public List<String> getRequestContents(User user, Long mandateId) {
        return mandateService.getMandateFiles(mandateId, user).stream().map( signatureFile -> {
            return episEnvelopePrefix +
                Jsoup.parse(
                    new String(signatureFile.content, StandardCharsets.UTF_8)
            ).head().getElementById("avaldus").html() +
            episEnvelopeSuffix;
        }).collect(Collectors.toList());
    }


    private String senderBic = "TULEVA20";
    private String recipientBic = "ECSDEE20";
    private String hubId = "TULEVA_1489s39923973127";

    private String episEnvelopePrefix = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Ex xmlns=\"urn:mhub:xsd:Envelope:01\" xmlns:ns2=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\">\n" +
            "                <BizMsg>\n" +
            "                                <ns2:AppHdr>\n" +
            "                                                <ns2:Fr>\n" +
            "                                                                <ns2:FIId>\n" +
            "                                                                                <ns2:FinInstnId>\n" +
            "                                                                                                <ns2:BICFI>"+ senderBic +"</ns2:BICFI>\n" +
            "                                                                                </ns2:FinInstnId>\n" +
            "                                                                </ns2:FIId>\n" +
            "                                                </ns2:Fr>\n" +
            "                                                <ns2:To>\n" +
            "                                                                <ns2:FIId>\n" +
            "                                                                                <ns2:FinInstnId>\n" +
            "                                                                                                <ns2:BICFI>"+recipientBic+"</ns2:BICFI>\n" +
            "                                                                                </ns2:FinInstnId>\n" +
            "                                                                </ns2:FIId>\n" +
            "                                                </ns2:To>\n" +
            "                                                <ns2:BizMsgIdr>"+hubId+"</ns2:BizMsgIdr>\n" +
            "                                                <ns2:MsgDefIdr>epis</ns2:MsgDefIdr>\n" +
            "                                                <ns2:CreDt>2017-03-13T10:00:39.731Z</ns2:CreDt>\n" +
            "                                </ns2:AppHdr>";


    private String episEnvelopeSuffix = "                </BizMsg>\n" +
            "</Ex>";

}
