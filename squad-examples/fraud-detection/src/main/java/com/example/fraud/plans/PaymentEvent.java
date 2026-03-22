package com.example.fraud.plans;
import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;

/** Incoming payment event from Kafka. */
@SquadPlan(description = "Incoming payment event")
public class PaymentEvent {
    @Required public String transactionId;
    @Required public String customerId;
    @Required public String amount;
    @Required public String currency;
    public String merchantId;
    public String merchantCategory;
    public String ipAddress;
    public String country;
    public String channel;  // ONLINE, POS, ATM
}