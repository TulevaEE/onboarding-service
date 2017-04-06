package ee.tuleva.onboarding.mandate.content

import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.mandate.MandateApplicationType
import ee.tuleva.onboarding.mandate.MandateFileService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class MandateXmlServiceSpec extends Specification {

    MandateFileService mandateFileService = Mock(MandateFileService)
    MandateXmlService mandateXmlService = new MandateXmlService(mandateFileService)

    def "getRequestContents: Get Mandate XMLs"() {

        given:
        User sampleUser = UserFixture.sampleUser()
        Long sampleMandateId = 123L

        1 * mandateFileService.getMandateFiles(sampleMandateId, sampleUser) >> sampleFiles()

        when:
        List<MandateXmlMessage> contents = mandateXmlService.getRequestContents(sampleUser, sampleMandateId)

        then:
        contents.get(0).message == mandateXmlService.episEnvelopePrefix(contents.get(0).processId) + sampleXmlContent + mandateXmlService.episEnvelopeSuffix
        contents.get(0).type == MandateApplicationType.TRANSFER
    }


    List<SignatureFile> sampleFiles() {
        return [new SignatureFile("file", "html/text", sampleHtmlContent.getBytes())]
    }

    String sampleXmlContent = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns="http://epis.x-road.ee/producer/" xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns2="urn:iso:std:iso:20022:tech:xsd:head.001.001.01" xmlns:x="http://x-road.ee/xsd/x-road.xsd">
            <soapenv:Header>
                <x:consumer xmlns="http://epis.x-road.ee/producer/" xmlns:ns2="urn:iso:std:iso:20022:tech:xsd:head.001.001.01" xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:x="http://x-road.ee/xsd/x-road.xsd">EVKTULEVA</x:consumer>
                <x:id xmlns="http://epis.x-road.ee/producer/" xmlns:ns2="urn:iso:std:iso:20022:tech:xsd:head.001.001.01" xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:x="http://x-road.ee/xsd/x-road.xsd">1deb15be-177c-1234-1234-1abea98ef4c8</x:id>
            </soapenv:Header>
            <soapenv:Body>
                <OSAKUTE_VAHETAMISE_AVALDUS>
                    <Request DocumentNumber="232" DocumentDate="2017-03-29">
                        <PersonalData PersonId="38015023762" ExtractFlag="N" LanguagePreference="EST" FirstName="First" Phone="3725521234" ContactPreference="E" E_MAIL="firs.last@mailinator.com" Name="Last"></PersonalData>
                        <Address Country="EE" PostalIndex="00000" AddressRow1="Liivalaia 1-11" Territory="0784" AddressRow2="TALLINN"></Address>
                        <ApplicationData Pillar="2" SourceISIN="EE3600103297">
                            <ApplicationRow Percentage="50" DestinationISIN="EE3600109435"></ApplicationRow>
                        </ApplicationData>
                    </Request>
                </OSAKUTE_VAHETAMISE_AVALDUS>
            </soapenv:Body>
        </soapenv:Envelope>"""

    String sampleHtmlContent = """
<!DOCTYPE HTML>

<html>
<head>
    <script id="avaldus" type="text/xml">${sampleXmlContent}
    </script>
    <title>Ületoomise avaldus</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />

    <style type="text/css">ol{margin:0;padding:0}table td,table th{padding:0}.c36{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:191.2pt;border-top-color:#000000;border-bottom-style:solid}.c24{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:59.2pt;border-top-color:#000000;border-bottom-style:solid}.c30{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:29.6pt;border-top-color:#000000;border-bottom-style:solid}.c11{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:274.1pt;border-top-color:#000000;border-bottom-style:solid}.c2{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:156pt;border-top-color:#000000;border-bottom-style:solid}.c21{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:55.5pt;border-top-color:#000000;border-bottom-style:solid}.c9{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:117.8pt;border-top-color:#000000;border-bottom-style:solid}.c33{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:81.8pt;border-top-color:#000000;border-bottom-style:solid}.c35{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:228pt;border-top-color:#000000;border-bottom-style:solid}.c23{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:72pt;border-top-color:#000000;border-bottom-style:solid}.c20{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:75pt;border-top-color:#000000;border-bottom-style:solid}.c10{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:312pt;border-top-color:#000000;border-bottom-style:solid}.c26{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:26.2pt;border-top-color:#000000;border-bottom-style:solid}.c27{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:240pt;border-top-color:#000000;border-bottom-style:solid}.c8{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:176.2pt;border-top-color:#000000;border-bottom-style:solid}.c19{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:234pt;border-top-color:#000000;border-bottom-style:solid}.c22{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:468pt;border-top-color:#000000;border-bottom-style:solid}.c13{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:27.8pt;border-top-color:#000000;border-bottom-style:solid}.c38{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:57pt;border-top-color:#000000;border-bottom-style:solid}.c25{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:174pt;border-top-color:#000000;border-bottom-style:solid}.c29{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:435.8pt;border-top-color:#000000;border-bottom-style:solid}.c17{border-right-style:solid;padding:5pt 5pt 5pt 5pt;border-bottom-color:#000000;border-top-width:1pt;border-right-width:1pt;border-left-color:#000000;vertical-align:top;border-right-color:#000000;border-left-width:1pt;border-top-style:solid;border-left-style:solid;border-bottom-width:1pt;width:27pt;border-top-color:#000000;border-bottom-style:solid}.c37{color:#000000;font-weight:400;text-decoration:none;vertical-align:baseline;font-size:11pt;font-family:"Arial";font-style:normal}.c5{color:#000000;font-weight:400;text-decoration:none;vertical-align:baseline;font-size:11pt;font-family:"Times New Roman";font-style:normal}.c3{color:#000000;font-weight:700;text-decoration:none;vertical-align:baseline;font-size:11pt;font-family:"Times New Roman";font-style:normal}.c18{padding-top:0pt;padding-bottom:0pt;line-height:1.15;orphans:2;widows:2;text-align:left;height:11pt}.c0{padding-top:0pt;padding-bottom:0pt;line-height:1.0;text-align:left;height:11pt}.c16{color:#000000;text-decoration:none;vertical-align:baseline;font-size:12pt;font-style:normal}.c12{padding-top:0pt;padding-bottom:0pt;line-height:0.8416666666666667;text-align:right}.c4{padding-top:0pt;padding-bottom:0pt;line-height:0.9958333333333332;text-align:left}.c31{padding-top:0pt;padding-bottom:0pt;line-height:0.9958333333333332;text-align:center}.c15{border-spacing:0;border-collapse:collapse;margin-right:auto}.c14{padding-top:0pt;padding-bottom:0pt;line-height:1.0;text-align:left}.c32{background-color:#ffffff;max-width:468pt;padding:72pt 72pt 72pt 72pt}.c6{font-weight:400;font-family:"Times New Roman"}.c39{font-weight:700;font-family:"Times New Roman"}.c1{height:0pt}.c28{height:11pt}.c40{height:23pt}.c34{height:20pt}.c7{height:21pt}.title{padding-top:0pt;color:#000000;font-size:26pt;padding-bottom:3pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}.subtitle{padding-top:0pt;color:#666666;font-size:15pt;padding-bottom:16pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}li{color:#000000;font-size:11pt;font-family:"Arial"}p{margin:0;color:#000000;font-size:11pt;font-family:"Arial"}h1{padding-top:20pt;color:#000000;font-size:20pt;padding-bottom:6pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}h2{padding-top:18pt;color:#000000;font-size:16pt;padding-bottom:6pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}h3{padding-top:16pt;color:#434343;font-size:14pt;padding-bottom:4pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}h4{padding-top:14pt;color:#666666;font-size:12pt;padding-bottom:4pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}h5{padding-top:12pt;color:#666666;font-size:11pt;padding-bottom:4pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;orphans:2;widows:2;text-align:left}h6{padding-top:12pt;color:#666666;font-size:11pt;padding-bottom:4pt;font-family:"Arial";line-height:1.15;page-break-after:avoid;font-style:italic;orphans:2;widows:2;text-align:left}</style>


</head>
<body class="c32">
<div>
    <p class="c18"><span class="c37"></span></p>
</div>
</body>
</html>
"""

}