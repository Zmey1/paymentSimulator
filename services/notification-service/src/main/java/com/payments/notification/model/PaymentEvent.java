package com.payments.notification.model;

public class PaymentEvent {

    public static final String TOPIC_CREATED = "payment.created";
    public static final String TOPIC_APPROVED = "payment.approved";
    public static final String TOPIC_FLAGGED = "payment.flagged";

    private String paymentId;
    private String sender;
    private String receiver;
    private double amount;
    private String paymentType;
    private String status;
    private long timestamp;

    public PaymentEvent() {
    }

    public PaymentEvent(String paymentId, String sender, String receiver,
                        double amount, String paymentType, String status, long timestamp) {
        this.paymentId = paymentId;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.paymentType = paymentType;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
