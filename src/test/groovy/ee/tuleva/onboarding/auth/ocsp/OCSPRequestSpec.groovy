package ee.tuleva.onboarding.auth.ocsp


import spock.lang.Specification

class OCSPRequestSpec extends Specification {

    def "Test if serial number is valid"() {
        given:
        OCSPRequest request = new OCSPRequest(caToCheckValid, "http://aia.sk.ee/esteid2018")
        def serialNumberToCheck = new BigInteger("55405445748554957191388863545919296810")
        def expectedResponse = "GOOD"

        when:
        def response = request.checkSerialNumber(serialNumberToCheck)

        then:
        response == expectedResponse
    }

    def "Test if serial number is expired"() {
        given:
        OCSPRequest request = new OCSPRequest(caToCheckInvalid, "http://aia.sk.ee/esteid2015")
        def serialNumberToCheck = new BigInteger("162404153479766496567566276823360513812")
        def expectedResponse = "REVOKED"

        when:
        def response = request.checkSerialNumber(serialNumberToCheck)

        then:
        response == expectedResponse
    }

    String caToCheckInvalid = "-----BEGIN CERTIFICATE-----\n" +
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
        "-----END CERTIFICATE-----";

    String caToCheckValid = "-----BEGIN CERTIFICATE-----\n" +
        "MIIFVzCCBLigAwIBAgIQdUf6rBR0S4tbo2bU/mZV7TAKBggqhkjOPQQDBDBaMQsw\n" +
        "CQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRh\n" +
        "DA5OVFJFRS0xMDc0NzAxMzEVMBMGA1UEAwwMRUUtR292Q0EyMDE4MB4XDTE4MDky\n" +
        "MDA5MjIyOFoXDTMzMDkwNTA5MTEwM1owWDELMAkGA1UEBhMCRUUxGzAZBgNVBAoM\n" +
        "ElNLIElEIFNvbHV0aW9ucyBBUzEXMBUGA1UEYQwOTlRSRUUtMTA3NDcwMTMxEzAR\n" +
        "BgNVBAMMCkVTVEVJRDIwMTgwgZswEAYHKoZIzj0CAQYFK4EEACMDgYYABAHHOBlv\n" +
        "7UrRPYP1yHhOb7RA/YBDbtgynSVMqYdxnFrKHUXh6tFkghvHuA1k2DSom1hE5kqh\n" +
        "B5VspDembwWDJBOQWQGOI/0t3EtccLYjeM7F9xOPdzUbZaIbpNRHpQgVBpFX0xpL\n" +
        "TgW27MpIMhU8DHBWFpeAaNX3eUpD4gC5cvhsK0RFEqOCAx0wggMZMB8GA1UdIwQY\n" +
        "MBaAFH4pVuc0knhOd+FvLjMqmHHB/TSfMB0GA1UdDgQWBBTZrHDbX36+lPig5L5H\n" +
        "otA0rZoqEjAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADCCAc0G\n" +
        "A1UdIASCAcQwggHAMAgGBgQAj3oBAjAJBgcEAIvsQAECMDIGCysGAQQBg5EhAQEB\n" +
        "MCMwIQYIKwYBBQUHAgEWFWh0dHBzOi8vd3d3LnNrLmVlL0NQUzANBgsrBgEEAYOR\n" +
        "IQEBAjANBgsrBgEEAYORfwEBATANBgsrBgEEAYORIQEBBTANBgsrBgEEAYORIQEB\n" +
        "BjANBgsrBgEEAYORIQEBBzANBgsrBgEEAYORIQEBAzANBgsrBgEEAYORIQEBBDAN\n" +
        "BgsrBgEEAYORIQEBCDANBgsrBgEEAYORIQEBCTANBgsrBgEEAYORIQEBCjANBgsr\n" +
        "BgEEAYORIQEBCzANBgsrBgEEAYORIQEBDDANBgsrBgEEAYORIQEBDTANBgsrBgEE\n" +
        "AYORIQEBDjANBgsrBgEEAYORIQEBDzANBgsrBgEEAYORIQEBEDANBgsrBgEEAYOR\n" +
        "IQEBETANBgsrBgEEAYORIQEBEjANBgsrBgEEAYORIQEBEzANBgsrBgEEAYORIQEB\n" +
        "FDANBgsrBgEEAYORfwEBAjANBgsrBgEEAYORfwEBAzANBgsrBgEEAYORfwEBBDAN\n" +
        "BgsrBgEEAYORfwEBBTANBgsrBgEEAYORfwEBBjAqBgNVHSUBAf8EIDAeBggrBgEF\n" +
        "BQcDCQYIKwYBBQUHAwIGCCsGAQUFBwMEMGoGCCsGAQUFBwEBBF4wXDApBggrBgEF\n" +
        "BQcwAYYdaHR0cDovL2FpYS5zay5lZS9lZS1nb3ZjYTIwMTgwLwYIKwYBBQUHMAKG\n" +
        "I2h0dHA6Ly9jLnNrLmVlL0VFLUdvdkNBMjAxOC5kZXIuY3J0MBgGCCsGAQUFBwED\n" +
        "BAwwCjAIBgYEAI5GAQEwMAYDVR0fBCkwJzAloCOgIYYfaHR0cDovL2Muc2suZWUv\n" +
        "RUUtR292Q0EyMDE4LmNybDAKBggqhkjOPQQDBAOBjAAwgYgCQgDeuUY4HczUbFKS\n" +
        "002HZ88gclgYdztHqglENyTMtXE6dMBRnCbgUmhBCAA0mJSHbyFJ8W9ikLiSyurm\n" +
        "kJM0hDE9KgJCASOqA405Ia5nKjTJPNsHQlMi7KZsIcTHOoBccx+54N8ZX1MgBozJ\n" +
        "mT59rZY/2/OeE163BAwD0UdUQAnMPP6+W3Vd\n" +
        "-----END CERTIFICATE-----"


}
