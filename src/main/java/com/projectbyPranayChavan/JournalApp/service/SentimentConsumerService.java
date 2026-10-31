package com.projectbyPranayChavan.JournalApp.service;

import com.projectbyPranayChavan.JournalApp.model.SentimentData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class SentimentConsumerService {

    private final EmailService emailService;

    public SentimentConsumerService(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "weekly-sentiments", groupId = "weekly-sentiment-group")
    public void consume(SentimentData sentimentData) {
        emailService.sendEmail(sentimentData.getEmail(), "Sentiment for previous week", sentimentData.getSentiment());
    }
}
