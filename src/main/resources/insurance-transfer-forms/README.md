# Insurer forms for ending a third pillar insurance contract

The insurers' own blank PDF forms, which `ee.tuleva.onboarding.insurance` fills in for the
person to sign. Each file is the insurer's published form, byte for byte as downloaded on
2026-10-18. When an insurer publishes a new version, download it from the same page, replace
the file, update its checksum here and run the form tests: they name every field the service
fills, so a renamed or removed field fails them.

| File | Insurer form | Source | SHA-256 |
|---|---|---|---|
| `compensa-contract-termination.pdf` | Compensa Life, Kindlustuslepingu lõpetamise avaldus (kehtib alates 01.10.2020) | https://www.compensalife.eu/pdf/EE/applications/avaldus%20-%20kindlustuslepingu%20l%C3%B5petamine.pdf | `06e3e7d0a2ab04f9e02fee73910169582762398980302bbfc20078af17bc2b45` |
| `ergo-benefit-payment.pdf` | ERGO Life Insurance, Väljamakse avaldus / Application for Benefit Payment (2026-05) | https://ergo.ee/files/web-public/2026-05/V%C3%A4ljamakse-avaldus.pdf | `998f0d9b12bb0087303ff65df50ee7dcf7b7a12c5f099a2e7c29d65ef986d09d` |
| `artea-pension-payout.pdf` | Artea Life Insurance, Kogumispensioni väljamaksmise avaldus | https://artea.ee/wp-content/uploads/2026/09/AVALDUS-Pension-SPKPIPJP-uhekordne-valjamakse.pdf | `b1ea4e3c396db01f7bcf9bf05385f059d0a01ffd9854ea914dd3c9ded93ed3a3` |
| `artea-know-your-customer.pdf` | Artea Life Insurance, Tunne oma klienti kliendiandmete vorm füüsilisele isikule (2026-05-05) | https://artea.ee/wp-content/uploads/2026/09/Tunne-oma-klienti-kusimustik-fuusilisele-isikule_EST-2026-05-05.pdf | `25a50d243e558749cbd159ede5fb26e88bb082249166e7f0da2fce64a325d5d2` |

## Font

The forms' own field fonts cannot write š and ž, so the filled text is set in Liberation Sans,
which has the same character widths as Arial and Helvetica, the fonts the forms were laid out
for. It is embedded in each filled form.

| File | Source | SHA-256 |
|---|---|---|
| `LiberationSans-Regular.ttf` | Liberation Fonts 2.1.5, `liberation-fonts-ttf-2.1.5.tar.gz` from https://github.com/liberationfonts/liberation-fonts/releases/tag/2.1.5 (archive SHA-256 `7191c669bf38899f73a2094ed00f7b800553364f90e2637010a69c0e268f25d0`) | `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8` |
| `LiberationSans-LICENSE.txt` | The `LICENSE` file of the same archive: SIL Open Font License 1.1, which allows bundling and embedding the font | `93fed46019c38bbe566b479d22148e2e8a1e85ada614accb0211c37b2c61c19b` |
