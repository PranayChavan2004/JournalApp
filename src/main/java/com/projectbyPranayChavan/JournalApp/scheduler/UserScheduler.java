package com.projectbyPranayChavan.JournalApp.scheduler;

import com.projectbyPranayChavan.JournalApp.cache.AppCache;
import com.projectbyPranayChavan.JournalApp.entities.JournalEntry;
import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.enums.Sentiment;
import com.projectbyPranayChavan.JournalApp.model.SentimentData;
import com.projectbyPranayChavan.JournalApp.repository.UserRepositoryImpl;
import com.projectbyPranayChavan.JournalApp.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserScheduler {

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private ObjectProvider<KafkaTemplate<String, SentimentData>> kafkaTemplateProvider;

    @Autowired
    private AppCache appCache;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    /* The scheduled annotation runs this method on a timer: every Sunday at 9 AM (zone from config) */
    @Scheduled(cron = "0 0 9 * * SUN", zone = "${app.scheduler.zone:Asia/Kolkata}")
    @Transactional(readOnly = true)
    public void fetchUsersAndSendSaMail() {
        LocalDateTime weekAgo = LocalDateTime.now(ZoneOffset.UTC).minus(7, ChronoUnit.DAYS);
        List<User> users = userRepository.getUserForSA();

        for (User user : users) {
            Map<Sentiment, Integer> counts = new EnumMap<>(Sentiment.class);
            for (JournalEntry entry : user.getJournalEntries()) {
                if (entry.getDate() != null && entry.getDate().isAfter(weekAgo) && entry.getSentiment() != null) {
                    counts.merge(entry.getSentiment(), 1, Integer::sum);
                }
            }

            Sentiment mostFrequent = null;
            int max = 0;
            for (Map.Entry<Sentiment, Integer> e : counts.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    mostFrequent = e.getKey();
                }
            }

            if (mostFrequent != null) {
                SentimentData data = SentimentData.builder()
                        .email(user.getEmail())
                        .sentiment("Sentiment for last 7 days: " + mostFrequent)
                        .build();
                deliver(data);
            }
        }
    }

    private void deliver(SentimentData data) {
        KafkaTemplate<String, SentimentData> kafkaTemplate = kafkaEnabled ? kafkaTemplateProvider.getIfAvailable() : null;
        if (kafkaTemplate == null) {
            emailService.sendEmail(data.getEmail(), "Sentiment for previous week", data.getSentiment());
            return;
        }
        try {
            kafkaTemplate.send("weekly-sentiments", data.getEmail(), data).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Kafka send failed, sending the e-mail directly");
                    emailService.sendEmail(data.getEmail(), "Sentiment for previous week", data.getSentiment());
                }
            });
        } catch (Exception e) {
            emailService.sendEmail(data.getEmail(), "Sentiment for previous week", data.getSentiment());
        }
    }

    @Scheduled(cron = "0 0/10 * * * *")
    public void clearAppCache() {
        appCache.init();
    }
}
