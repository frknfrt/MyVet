package com.vetos.modules.patient.domain;

/**
 * TC Kimlik No (Turkiye Cumhuriyeti kimlik numarasi) resmi checksum
 * algoritmasi. 11 haneli, ilk hane sifir olamaz; 10. ve 11. haneler
 * onceki hanelerden turetilen kontrol basamaklaridir.
 */
final class TcKimlikValidator {

    private TcKimlikValidator() {
    }

    static boolean isValid(String candidate) {
        if (candidate == null || !candidate.matches("\\d{11}") || candidate.charAt(0) == '0') {
            return false;
        }

        int[] d = candidate.chars().map(c -> c - '0').toArray();

        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
        int evenSum = d[1] + d[3] + d[5] + d[7];

        int expectedD10 = ((oddSum * 7) - evenSum) % 10;
        int expectedD11 = (oddSum + evenSum + expectedD10) % 10;

        return d[9] == expectedD10 && d[10] == expectedD11;
    }
}
