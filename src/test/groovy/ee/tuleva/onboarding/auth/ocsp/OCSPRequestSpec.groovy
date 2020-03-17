package ee.tuleva.onboarding.auth.ocsp


import spock.lang.Specification

import java.security.cert.X509Certificate

class OCSPRequestSpec extends Specification {

    def "Test if certificate is valid for (TEST of ESTEID2018.pem.cer)"() {
        given:
        OCSPRequest request = new OCSPRequest(caToCheckValid, "http://aia.demo.sk.ee/ee-govca2018")
        def serialNumberToCheck = new BigInteger("71907861382765673730662460475615088197")
        def expectedResponse = "GOOD"

        when:
        def response = request.checkSerialNumber(serialNumberToCheck)

        then:
        response == expectedResponse
    }

    def "Test if certificate is revoked for (https://revoked.grc.com/)"() {
        given:
        OCSPRequest request = new OCSPRequest(caToCheckRevoked, "http://ocsp.digicert.com")
        def serialNumberToCheck = new BigInteger("1899175679850591481995995842307011259")
        def expectedResponse = "CERTIFICATE_REVOKED"

        when:
        def response = request.checkSerialNumber(serialNumberToCheck)

        then:
        response == expectedResponse
    }

    def "Test if certificate is expired"() {
        given:
        OCSPUtils ocspUtils = new OCSPUtils();
        X509Certificate cert = ocspUtils.getX509Certificate(expiredCert);
        OCSPRequest request = new OCSPRequest(caToCheckExpired, "http://aia.sk.ee/esteid2015")
        def expectedResponse = "CERTIFICATE_EXPIRED"

        when:
        def response = request.checkCertificate(cert)

        then:
        response == expectedResponse
    }

    String caToCheckRevoked = "-----BEGIN CERTIFICATE-----\n" +
        "MIIElDCCA3ygAwIBAgIQAf2j627KdciIQ4tyS8+8kTANBgkqhkiG9w0BAQsFADBh\n" +
        "MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3\n" +
        "d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBD\n" +
        "QTAeFw0xMzAzMDgxMjAwMDBaFw0yMzAzMDgxMjAwMDBaME0xCzAJBgNVBAYTAlVT\n" +
        "MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxJzAlBgNVBAMTHkRpZ2lDZXJ0IFNIQTIg\n" +
        "U2VjdXJlIFNlcnZlciBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
        "ANyuWJBNwcQwFZA1W248ghX1LFy949v/cUP6ZCWA1O4Yok3wZtAKc24RmDYXZK83\n" +
        "nf36QYSvx6+M/hpzTc8zl5CilodTgyu5pnVILR1WN3vaMTIa16yrBvSqXUu3R0bd\n" +
        "KpPDkC55gIDvEwRqFDu1m5K+wgdlTvza/P96rtxcflUxDOg5B6TXvi/TC2rSsd9f\n" +
        "/ld0Uzs1gN2ujkSYs58O09rg1/RrKatEp0tYhG2SS4HD2nOLEpdIkARFdRrdNzGX\n" +
        "kujNVA075ME/OV4uuPNcfhCOhkEAjUVmR7ChZc6gqikJTvOX6+guqw9ypzAO+sf0\n" +
        "/RR3w6RbKFfCs/mC/bdFWJsCAwEAAaOCAVowggFWMBIGA1UdEwEB/wQIMAYBAf8C\n" +
        "AQAwDgYDVR0PAQH/BAQDAgGGMDQGCCsGAQUFBwEBBCgwJjAkBggrBgEFBQcwAYYY\n" +
        "aHR0cDovL29jc3AuZGlnaWNlcnQuY29tMHsGA1UdHwR0MHIwN6A1oDOGMWh0dHA6\n" +
        "Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEdsb2JhbFJvb3RDQS5jcmwwN6A1\n" +
        "oDOGMWh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEdsb2JhbFJvb3RD\n" +
        "QS5jcmwwPQYDVR0gBDYwNDAyBgRVHSAAMCowKAYIKwYBBQUHAgEWHGh0dHBzOi8v\n" +
        "d3d3LmRpZ2ljZXJ0LmNvbS9DUFMwHQYDVR0OBBYEFA+AYRyCMWHVLyjnjUY4tCzh\n" +
        "xtniMB8GA1UdIwQYMBaAFAPeUDVW0Uy7ZvCj4hsbw5eyPdFVMA0GCSqGSIb3DQEB\n" +
        "CwUAA4IBAQAjPt9L0jFCpbZ+QlwaRMxp0Wi0XUvgBCFsS+JtzLHgl4+mUwnNqipl\n" +
        "5TlPHoOlblyYoiQm5vuh7ZPHLgLGTUq/sELfeNqzqPlt/yGFUzZgTHbO7Djc1lGA\n" +
        "8MXW5dRNJ2Srm8c+cftIl7gzbckTB+6WohsYFfZcTEDts8Ls/3HB40f/1LkAtDdC\n" +
        "2iDJ6m6K7hQGrn2iWZiIqBtvLfTyyRRfJs8sjX7tN8Cp1Tm5gr8ZDOo0rwAhaPit\n" +
        "c+LJMto4JQtV05od8GiG7S5BNO98pVAdvzr508EIDObtHopYJeS4d60tbvVS3bR0\n" +
        "j6tJLp07kzQoH3jOlOrHvdPJbRzeXDLz\n" +
        "-----END CERTIFICATE-----";

    String expiredCert = "-----BEGIN CERTIFICATE-----\n" +
        "MIIF1DCCA7ygAwIBAgIQei3m+GlFK/BaGs7yb2ynFDANBgkqhkiG9w0BAQsFADBj\n" +
        "MQswCQYDVQQGEwJFRTEiMCAGA1UECgwZQVMgU2VydGlmaXRzZWVyaW1pc2tlc2t1\n" +
        "czEXMBUGA1UEYQwOTlRSRUUtMTA3NDcwMTMxFzAVBgNVBAMMDkVTVEVJRC1TSyAy\n" +
        "MDE1MB4XDTE3MTEyNjE0MjU1NFoXDTIwMDEwNzIxNTk1OVowgY0xCzAJBgNVBAYT\n" +
        "AkVFMQ8wDQYDVQQKDAZFU1RFSUQxFzAVBgNVBAsMDmF1dGhlbnRpY2F0aW9uMR8w\n" +
        "HQYDVQQDDBZTQUFSLFJJU1RPLDM4ODAyMTM1NzE4MQ0wCwYDVQQEDARTQUFSMQ4w\n" +
        "DAYDVQQqDAVSSVNUTzEUMBIGA1UEBRMLMzg4MDIxMzU3MTgwdjAQBgcqhkjOPQIB\n" +
        "BgUrgQQAIgNiAAQOdy7hJNdAfx6kGhNMnJbypXrDT3QAnm7PVeqBaLNtbZH2iec9\n" +
        "Pgznb8aO2b7jx2CwK5z0XPYZO1g7eNWNFf50pW0uM2CaGZOnMOn4rUv0Aa6pd6Wv\n" +
        "PrC95364N6uX0DyjggIFMIICATAJBgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIDiDBT\n" +
        "BgNVHSAETDBKMD4GCSsGAQQBzh8BATAxMC8GCCsGAQUFBwIBFiNodHRwczovL3d3\n" +
        "dy5zay5lZS9yZXBvc2l0b29yaXVtL0NQUzAIBgYEAI96AQIwIAYDVR0RBBkwF4EV\n" +
        "cmlzdG8uc2Fhci4xQGVlc3RpLmVlMB0GA1UdDgQWBBS2pIo7izdVdX3C+tWQV2KB\n" +
        "mcd7+zAgBgNVHSUBAf8EFjAUBggrBgEFBQcDAgYIKwYBBQUHAwQwHwYDVR0jBBgw\n" +
        "FoAUs6uIvJnVYqSFKgjNtB1yO4NyR1EwYQYIKwYBBQUHAQMEVTBTMFEGBgQAjkYB\n" +
        "BTBHMEUWP2h0dHBzOi8vc2suZWUvZW4vcmVwb3NpdG9yeS9jb25kaXRpb25zLWZv\n" +
        "ci11c2Utb2YtY2VydGlmaWNhdGVzLxMCRU4wagYIKwYBBQUHAQEEXjBcMCcGCCsG\n" +
        "AQUFBzABhhtodHRwOi8vYWlhLnNrLmVlL2VzdGVpZDIwMTUwMQYIKwYBBQUHMAKG\n" +
        "JWh0dHA6Ly9jLnNrLmVlL0VTVEVJRC1TS18yMDE1LmRlci5jcnQwPAYDVR0fBDUw\n" +
        "MzAxoC+gLYYraHR0cDovL3d3dy5zay5lZS9jcmxzL2VzdGVpZC9lc3RlaWQyMDE1\n" +
        "LmNybDANBgkqhkiG9w0BAQsFAAOCAgEAGSN0ffLc+nAWhamPXoV0BGghs/9n7CGv\n" +
        "Oa9LzVPSEV9ttF28Aklj3pPWxRgVB0PhWRTlvn65z5EFQuznGXZq1Z4GOzDNavqJ\n" +
        "y8HApEyVEjsyqdrYNjepEKjtlmvI9koVD9IcUE8gHzj3L6oNOF7ovTuTSwzoprEc\n" +
        "T7Tf3DZuamhPTKYHsKRfcBUIq0V93kZmhXP3pDqlC4BRyrhuNYBF8Ja8inzcxdpX\n" +
        "rpSx+ffLHIOUGtaerIe6Q77YzVQD+tXNKhhTQ4OEFb9zVR9h93zC72ywC7MEQKHa\n" +
        "eB+MsM/x1btjvSIZsXjlVdFOg/ibyulFkW6OJK5heErQs+L3zZltIBhpVu4b+Lmg\n" +
        "VZZViLwbqEN4I9vH4AhT2hQjjSVAT2pqaqKL8FkZ9cGHrUPqU75tZRh/5qy2tsw2\n" +
        "/eHFvNgzOm964Ke3GvLaWZyR75iW8SZE8TAZOzte9tk3aAn0rvSE7EPpP3HMY7ve\n" +
        "3Au0kaaGnbsreNxCIAOhRqI0Gqz1hW0/e07avPRjGYEVeSwaN/IRCtApM3ITx3WZ\n" +
        "/YpBFO5cUSabcsNB5rZC4Geh7wzFn/LUS/YdgK5jlGIA4bSNuLPBS5x7sYa/NkUc\n" +
        "Zd+1Q7NlOapw4Yy+Jc8mdEUFmJ+x9xD4Mvr6ENh/n2TNrZjSgRxxhlqW3bCzZQKT\n" +
        "zQ19uKV+gzs=\n" +
        "-----END CERTIFICATE-----"

    String caToCheckExpired = "-----BEGIN CERTIFICATE-----\n" +
        "MIIGcDCCBVigAwIBAgIQRUgJC4ec7yFWcqzT3mwbWzANBgkqhkiG9w0BAQwFADB1\n" +
        "MQswCQYDVQQGEwJFRTEiMCAGA1UECgwZQVMgU2VydGlmaXRzZWVyaW1pc2tlc2t1\n" +
        "czEoMCYGA1UEAwwfRUUgQ2VydGlmaWNhdGlvbiBDZW50cmUgUm9vdCBDQTEYMBYG\n" +
        "CSqGSIb3DQEJARYJcGtpQHNrLmVlMCAXDTE1MTIxNzEyMzg0M1oYDzIwMzAxMjE3\n" +
        "MjM1OTU5WjBjMQswCQYDVQQGEwJFRTEiMCAGA1UECgwZQVMgU2VydGlmaXRzZWVy\n" +
        "aW1pc2tlc2t1czEXMBUGA1UEYQwOTlRSRUUtMTA3NDcwMTMxFzAVBgNVBAMMDkVT\n" +
        "VEVJRC1TSyAyMDE1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0oH6\n" +
        "1NDxbdW9k8nLA1qGaL4B7vydod2Ewp/STBZB3wEtIJCLdkpEsS8pXfFiRqwDVsgG\n" +
        "Gbu+Q99trlb5LI7yi7rIkRov5NftBdSNPSU5rAhYPQhvZZQgOwRaHa5Ey+BaLJHm\n" +
        "LqYQS9hQvQsCYyws+xVvNFUpK0pGD64iycqdMuBl/nWq3fLuZppwBh0VFltm4nhr\n" +
        "/1S0R9TRJpqFUGbGr4OK/DwebQ5PjhdS40gCUNwmC7fPQ4vIH+x+TCk2aG+u3MoA\n" +
        "z0IrpVWqiwzG/vxreuPPAkgXeFCeYf6fXLsGz4WivsZFbph2pMjELu6sltlBXfAG\n" +
        "3fGv43t91VXicyzR/eT5dsB+zFsW1sHV+1ONPr+qzgDxCH2cmuqoZNfIIq+buob3\n" +
        "eA8ee+XpJKJQr+1qGrmhggjvAhc7m6cU4x/QfxwRYhIVNhJf+sKVThkQhbJ9XxuK\n" +
        "k3c18wymwL1mpDD0PIGJqlssMeiuJ4IzagFbgESGNDUd4icm0hQT8CmQeUm1GbWe\n" +
        "BYseqPhMQX97QFBLXJLVy2SCyoAz7Bq1qA43++EcibN+yBc1nQs2Zoq8ck9MK0bC\n" +
        "xDMeUkQUz6VeQGp69ImOQrsw46qTz0mtdQrMSbnkXCuLan5dPm284J9HmaqiYi6j\n" +
        "6KLcZ2NkUnDQFesBVlMEm+fHa2iR6lnAFYZ06UECAwEAAaOCAgowggIGMB8GA1Ud\n" +
        "IwQYMBaAFBLyWj7qVhy/zQas8fElyalL1BSZMB0GA1UdDgQWBBSzq4i8mdVipIUq\n" +
        "CM20HXI7g3JHUTAOBgNVHQ8BAf8EBAMCAQYwdwYDVR0gBHAwbjAIBgYEAI96AQIw\n" +
        "CQYHBACL7EABAjAwBgkrBgEEAc4fAQEwIzAhBggrBgEFBQcCARYVaHR0cHM6Ly93\n" +
        "d3cuc2suZWUvQ1BTMAsGCSsGAQQBzh8BAjALBgkrBgEEAc4fAQMwCwYJKwYBBAHO\n" +
        "HwEEMBIGA1UdEwEB/wQIMAYBAf8CAQAwQQYDVR0eBDowOKE2MASCAiIiMAqHCAAA\n" +
        "AAAAAAAAMCKHIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMCcGA1Ud\n" +
        "JQQgMB4GCCsGAQUFBwMJBggrBgEFBQcDAgYIKwYBBQUHAwQwfAYIKwYBBQUHAQEE\n" +
        "cDBuMCAGCCsGAQUFBzABhhRodHRwOi8vb2NzcC5zay5lZS9DQTBKBggrBgEFBQcw\n" +
        "AoY+aHR0cDovL3d3dy5zay5lZS9jZXJ0cy9FRV9DZXJ0aWZpY2F0aW9uX0NlbnRy\n" +
        "ZV9Sb290X0NBLmRlci5jcnQwPQYDVR0fBDYwNDAyoDCgLoYsaHR0cDovL3d3dy5z\n" +
        "ay5lZS9yZXBvc2l0b3J5L2NybHMvZWVjY3JjYS5jcmwwDQYJKoZIhvcNAQEMBQAD\n" +
        "ggEBAHRWDGI3P00r2sOnlvLHKk9eE7X93eT+4e5TeaQsOpE5zQRUTtshxN8Bnx2T\n" +
        "oQ9rgi18q+MwXm2f0mrGakYYG0bix7ZgDQvCMD/kuRYmwLGdfsTXwh8KuL6uSHF+\n" +
        "U/ZTss6qG7mxCHG9YvebkN5Yj/rYRvZ9/uJ9rieByxw4wo7b19p22PXkAkXP5y3+\n" +
        "qK/Oet98lqwI97kJhiS2zxFYRk+dXbazmoVHnozYKmsZaSUvoYNNH19tpS7BLdsg\n" +
        "i9KpbvQLb5ywIMq9ut3+b2Xvzq8yzmHMFtLIJ6Afu1jJpqD82BUAFcvi5vhnP8M7\n" +
        "b974R18WCOpgNQvXDI+2/8ZINeU=\n" +
        "-----END CERTIFICATE-----"

    String caToCheckValid = "-----BEGIN CERTIFICATE-----\n" +
        "MIIFLDCCBI2gAwIBAgIQImvqKVwtGyZbh+ecdKPc7zAKBggqhkjOPQQDBDBiMQsw\n" +
        "CQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRh\n" +
        "DA5OVFJFRS0xMDc0NzAxMzEdMBsGA1UEAwwUVEVTVCBvZiBFRS1Hb3ZDQTIwMTgw\n" +
        "HhcNMTgwODMwMTI0ODI4WhcNMzMwODMwMTI0ODI4WjBiMQswCQYDVQQGEwJFRTEb\n" +
        "MBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRhDA5OVFJFRS0xMDc0\n" +
        "NzAxMzEdMBsGA1UEAwwUVEVTVCBvZiBFRS1Hb3ZDQTIwMTgwgZswEAYHKoZIzj0C\n" +
        "AQYFK4EEACMDgYYABABZN0DFpEKsj3SzsySoR/bcwAUoLc+S2HrvHY0xIDkFFTtU\n" +
        "QXfjxXyexNIx+ALe2IYJZLTl0T79C5by4/mO/5H7UgCxZZCRKtdcKqSGYJOVpT0X\n" +
        "oA51yX8eBk8aPVrTcwABcBhU6nTNGEoNXfeS7mrZB6Gs3eFxEVdejIEjNObWVFYM\n" +
        "bqOCAuAwggLcMBIGA1UdEwEB/wQIMAYBAf8CAQEwDgYDVR0PAQH/BAQDAgEGMDQG\n" +
        "A1UdJQEB/wQqMCgGCCsGAQUFBwMJBggrBgEFBQcDAgYIKwYBBQUHAwQGCCsGAQUF\n" +
        "BwMBMB0GA1UdDgQWBBR/DHDY9OWPAXfux20pKbn0yfxqwDAfBgNVHSMEGDAWgBR/\n" +
        "DHDY9OWPAXfux20pKbn0yfxqwDCCAiQGA1UdIASCAhswggIXMAgGBgQAj3oBAjAJ\n" +
        "BgcEAIvsQAECMDIGCysGAQQBg5EhAQIBMCMwIQYIKwYBBQUHAgEWFWh0dHBzOi8v\n" +
        "d3d3LnNrLmVlL0NQUzANBgsrBgEEAYORIQECAjANBgsrBgEEAYORfwECATANBgsr\n" +
        "BgEEAYORIQECBTANBgsrBgEEAYORIQECBjANBgsrBgEEAYORIQECBzANBgsrBgEE\n" +
        "AYORIQECAzANBgsrBgEEAYORIQECBDANBgsrBgEEAYORIQECCDANBgsrBgEEAYOR\n" +
        "IQECCTANBgsrBgEEAYORIQECCjANBgsrBgEEAYORIQECCzANBgsrBgEEAYORIQEC\n" +
        "DDANBgsrBgEEAYORIQECDTANBgsrBgEEAYORIQECDjANBgsrBgEEAYORIQECDzAN\n" +
        "BgsrBgEEAYORIQECEDANBgsrBgEEAYORIQECETANBgsrBgEEAYORIQECEjANBgsr\n" +
        "BgEEAYORIQECEzANBgsrBgEEAYORIQECFDANBgsrBgEEAYORfwECAjANBgsrBgEE\n" +
        "AYORfwECAzANBgsrBgEEAYORfwECBDANBgsrBgEEAYORfwECBTANBgsrBgEEAYOR\n" +
        "fwECBjBVBgorBgEEAYORIQoBMEcwIQYIKwYBBQUHAgEWFWh0dHBzOi8vd3d3LnNr\n" +
        "LmVlL0NQUzAiBggrBgEFBQcCAjAWGhRURVNUIG9mIEVFLUdvdkNBMjAxODAYBggr\n" +
        "BgEFBQcBAwQMMAowCAYGBACORgEBMAoGCCqGSM49BAMEA4GMADCBiAJCAeTjfRrM\n" +
        "t+4ecVYozAfdpTjCikf332XcuRkuJ6fbLqqMm7C3v/d5ebyOqvDG6wWAp8Z0GZA5\n" +
        "ONIvS2rm8kJ7HR5tAkIAoFn7n5ZW62dXMmPk+LReR1hUyTpxrxC31QjqvMqM2AbM\n" +
        "8luw0f/AaC5qsEdwKrKT+p1xvnjSyIVfcMiu6Q3T2EE=\n" +
        "-----END CERTIFICATE-----"


}
