package com.vetos.platform.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * cPanel (veya herhangi bir) SMTP sunucusuyla HTML e-posta gonderen kucuk,
 * paylasilan yardimci -- admin-email (SmtpAdminEmailAdapter) ve davet
 * e-postasi (SmtpInviteEmailAdapter) AYNI mekanigi kullanir, kimlik
 * bilgilerini her cagiran kendi portundan/config'inden okuyup gecirir.
 * Kimlik dogrulama her gonderimde yeniden kurulur (JavaMailSenderImpl
 * durumsuz, hacim dusuk oldugu icin performans sorunu degil).
 */
@Component
public class SmtpMailClient {

    public void send(
        String smtpHost, int smtpPort, String username, String password,
        String fromAddress, String recipientEmail, String subject, String html
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpHost);
        sender.setPort(smtpPort);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        if (smtpPort == 465) {
            // Port 465 = implicit SSL/TLS (baglanti bastan sifreli kurulur).
            sender.getJavaMailProperties().put("mail.smtp.ssl.enable", "true");
            sender.getJavaMailProperties().put("mail.smtp.socketFactory.port", String.valueOf(smtpPort));
            sender.getJavaMailProperties().put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            sender.getJavaMailProperties().put("mail.smtp.socketFactory.fallback", "false");
        } else {
            // Diger portlar (orn. 587) = STARTTLS (duz baglan, sonra sifrele).
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            throw new SmtpSendException("SMTP gonderimi basarisiz: " + fromAddress + " -> " + recipientEmail, e);
        }
    }
}
