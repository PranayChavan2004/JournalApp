package com.projectbyPranayChavan.JournalApp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(body);

            javaMailSender.send(mail);
            log.info("Weekly mood e-mail sent");   // the address is not logged: it is personal data
        } catch (Exception e) {
            log.error("Could not send e-mail: {}", e.getMessage());
        }
    }
}
