package io.sentinel.backend.ingestion;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.service.MentionProcessingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class MockMentionIngestionService {
    private final MentionProcessingService processor;
    @Value("${sentinel.mock.enabled:true}") private boolean mockEnabled;
    @Value("${sentinel.handle:@AirtelPaymentsBank}") private String handle;
    private final Random rnd = new Random();
    private static final String[][] MOCK_MENTIONS = {
        {"NEGATIVE", "@AirtelPaymentsBank my UPI payment of ₹5000 failed but money got deducted! This is a scam. #AirtelDown @RBI_Informs", "angryshopper99", "8420"},
        {"NEGATIVE", "@AirtelPaymentsBank your app keeps crashing every time I try to recharge. Totally useless app. Fix it!", "frustrated_user", "1200"},
        {"NEGATIVE", "@AirtelPaymentsBank I've been waiting 48 hours for KYC verification. Zero response from support. Pathetic service.", "rahul_sharma_in", "5600"},
        {"POSITIVE", "@AirtelPaymentsBank just used the new FD feature — super easy and great rates! Well done team 🎉 #DigitalBanking", "happy_investor", "3200"},
        {"POSITIVE", "@AirtelPaymentsBank customer service agent Priya was so helpful! Resolved my issue in minutes. Impressed!", "satisfied_customer", "890"},
        {"NEUTRAL",  "@AirtelPaymentsBank can I link my savings account from another bank to your wallet? Asking for a friend.", "curious_user", "450"},
        {"NEGATIVE", "@AirtelPaymentsBank wrong amount debited from my account TWICE. Total fraud! Taking this to consumer forum. @RBI_Informs @finmin", "angry_customer99", "12500"},
        {"NEGATIVE", "@AirtelPaymentsBank your API is down for the 3rd time this week. Merchants are losing business. Sort this out!", "merchant_delhi", "25000"},
        {"POSITIVE", "@AirtelPaymentsBank the new cashback offer on electricity bills is amazing! Got ₹150 back. Keep it up!", "smart_saver", "2100"},
        {"NEUTRAL",  "@AirtelPaymentsBank what is the daily transaction limit on your prepaid wallet?", "new_user_2024", "120"},
    };
    public MockMentionIngestionService(MentionProcessingService processor) {
        this.processor = processor;
    }
    @Scheduled(fixedDelayString = "${sentinel.polling.interval-ms:30000}")
    public void ingestMockMention() {
        if (!mockEnabled) return;
        String[] mock = MOCK_MENTIONS[rnd.nextInt(MOCK_MENTIONS.length)];
        MentionEntity m = new MentionEntity();
        m.id              = "MOCK-" + UUID.randomUUID().toString().substring(0,8);
        m.platform        = "TWITTER";
        m.handle          = handle;
        m.authorUsername  = mock[2];
        m.authorName      = mock[2].replace("_", " ");
        m.authorFollowers = Long.parseLong(mock[3]);
        m.text            = mock[1];
        m.language        = "en";
        m.postedAt        = Instant.now().minusSeconds(rnd.nextInt(300));
        m.url             = "https://twitter.com/" + mock[2] + "/status/" + System.currentTimeMillis();
        m.likeCount       = rnd.nextInt(50);
        m.retweetCount    = rnd.nextInt(20);
        m.processingStatus = "NEW";
        System.out.println("[MockIngestion] Ingesting: " + m.text.substring(0, Math.min(60, m.text.length())) + "...");
        new Thread(() -> processor.process(m)).start();
    }
    public void ingestCustomMention(String text, String author, long followers) {
        MentionEntity m = new MentionEntity();
        m.id = "CUSTOM-" + UUID.randomUUID().toString().substring(0,8);
        m.platform = "TWITTER"; m.handle = handle;
        m.authorUsername = author; m.authorName = author;
        m.authorFollowers = followers;
        m.text = text;
        m.language = "en"; m.postedAt = Instant.now();
        m.url = "https://twitter.com/" + author; m.likeCount = 0; m.retweetCount = 0;
        m.processingStatus = "NEW";
        new Thread(() -> processor.process(m)).start();
    }
}