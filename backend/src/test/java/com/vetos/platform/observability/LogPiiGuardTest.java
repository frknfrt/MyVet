package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kaynak taramasi (spec 2026-10-14 S4.5): log cagrilarina telefon/e-posta/yanit govdesi
 * LogMasking'den gecmeden verilirse kirilir. Hata mesaji dosya:satir listesi verir.
 */
class LogPiiGuardTest {

    private static final Pattern LOG_CALL_START = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern MASK_CALL = Pattern.compile("LogMasking\\.\\w+\\(");
    private static final Pattern SENSITIVE = Pattern.compile(
        "[\\w.]*?(?:getResponseBodyAsString\\(\\)|recipientContact|\\w*(?:[pP]hone|[eE]mail)\\w*)");
    private static final Pattern MASKED_PREFIX = Pattern.compile("LogMasking\\.M\\(\\s*$");

    static List<String> violations(String source, String fileName) {
        List<String> found = new ArrayList<>();
        Matcher start = LOG_CALL_START.matcher(source);
        while (start.find()) {
            int end = callEnd(source, start.end() - 1);
            String args = STRING_LITERAL.matcher(source.substring(start.end(), end)).replaceAll("\"\"");
            args = MASK_CALL.matcher(args).replaceAll("LogMasking.M(");
            Matcher m = SENSITIVE.matcher(args);
            while (m.find()) {
                if (!MASKED_PREFIX.matcher(args.substring(0, m.start())).find()) {
                    found.add(fileName + ":" + lineOf(source, start.start()) + " -> " + m.group());
                }
            }
        }
        return found;
    }

    private static int callEnd(String s, int openParen) {
        int depth = 0;
        char quote = 0;
        for (int i = openParen; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return s.length();
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Test
    void should_flagUnmaskedSensitiveArguments() {
        assertThat(violations("log.warn(\"x={}\", request.recipientContact());", "A.java")).hasSize(1);
        assertThat(violations("log.info(\n  \"to={}\",\n  ownerEmail\n);", "B.java")).containsExactly("B.java:1 -> ownerEmail");
        assertThat(violations("log.warn(\"g={}\", e.getResponseBodyAsString());", "C.java")).hasSize(1);
        assertThat(violations("log.info(\"a={}, b={}\", LogMasking.phone(p), recipientPhone);", "D.java")).hasSize(1);
    }

    @Test
    void should_acceptMaskedArgumentsAndPlainText() {
        assertThat(violations("log.warn(\"x={}\", LogMasking.phone(request.recipientContact()));", "A.java")).isEmpty();
        assertThat(violations("log.info(\"to={}\", LogMasking.email(invite.getEmail()));", "B.java")).isEmpty();
        assertThat(violations("log.warn(\"g={}\", LogMasking.body(e.getResponseBodyAsString()));", "C.java")).isEmpty();
        assertThat(violations("log.info(\"email gonderildi, phone yok\");", "D.java")).isEmpty();
    }

    @Test
    void should_haveNoUnmaskedPersonalDataInLogs() throws IOException {
        List<String> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                all.addAll(violations(Files.readString(file), file.toString().replace('\\', '/')));
            }
        }
        assertThat(all).as("Maskelenmemis kisisel veri iceren log satirlari (LogMasking kullanin)").isEmpty();
    }
}
