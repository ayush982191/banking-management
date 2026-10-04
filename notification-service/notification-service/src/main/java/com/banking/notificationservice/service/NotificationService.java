package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(
            @Payload Map<String ,Object> payload
            ){
       try{
           String accountNumber = (String) payload.get("accountNumber");
           String otp = (String) payload.get("otp");
           String transactionId = (String) payload.get("transactionId");
           String amount = payload.get("amount").toString();
           String reason = (String) payload.get("reason");
           sendAlert(
                   accountNumber,
                   "TRANSACTION_VERIFICATION_REQUIRED",
                   String.format(
                           "Suspicious activity detected on your account"+
                                   "Reason %s"+
                                   "A transaction of %s is pending verification "+
                                   "Your otp is %s and valid for 5minutes"+
                                   "If this wasn't you- Ignore this message"

                   )
           );
       } catch (Exception e) {
           log.error("Error sending OTP Notification "+e.getMessage());
       }
    }
    @KafkaListener(topics = "transactoin.completed" )
    public void consumeTransactionCompleted(
            @Payload Map<String ,Object> payload
    ){
        try{
            String senderAccount = (String) payload.get("senderAccountNumber");
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            String amount = payload.get("amount").toString();
            //          debit alert
            sendAlert(senderAccount,"DEBIT ALERT",
                    String.format("%s debited from account %s",amount,senderAccount)
                    );
            // credit alert
            sendAlert(senderAccount,"CREDIT ALERT",
                    String.format("%s credited from account %s",amount,receiverAccount)
            );



        } catch (Exception e) {
            log.error("Error sending transaction notification {} ",e.getMessage());
        }
    }
    @KafkaListener(topics = "fraud.detected")
    public void consumerFraudDetected(
            @Payload Map<String ,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            sendAlert(accountNumber,"SUSPICIOUS ACTIVITY DETECTED",
                    String.format("your account %s has been blocked and reason is %s",reason+" please contact your bank immediately")
                    );



        } catch (Exception e) {
//            throw new RuntimeException(e);
            log.error("Error sending fraud alert "+e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(
            @Payload Map<String ,Object> payload
    ){
        try{
            String senderAccount = (String) payload.get("senderAccountNumber");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");
            sendAlert(senderAccount,"REFUND PROCESSED",
                    String.format("Your transaction of %s was cancelled ",amount," has been refunded to account %s ",senderAccount," and reason is %s",reason)
            );
        } catch (Exception e) {
//            throw new RuntimeException(e);
            log.error("Error sending notification "+e.getMessage());
        }

    }
    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
        @Payload Map<String ,Object> payload
    ){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();
            String paymentId = (String) payload.get("paymentId");
            String razorpayPaymentId = (String) payload.get("razorpayPaymentId");

            sendAlert(
                    accountNumber,
                    "PAYMENT RECEIVED",
                    String.format("Payment of %s has been successfully processed for account %s. Payment ID: %s, Razorpay ID: %s",
                            amount, accountNumber, paymentId, razorpayPaymentId)
            );
            log.info("Payment completed notification sent for account: {}", accountNumber);
        } catch (Exception e) {
            log.error("Error sending payment completed notification: {}", e.getMessage());
        }
    }
    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(
            @Payload Map<String, Object> payload
    ) {
        try {
            String paymentId = (String) payload.get("paymentId");
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount") != null ? payload.get("amount").toString() : "";
            String reason = (String) payload.get("reason");

            sendAlert(
                    accountNumber,
                    "PAYMENT FAILED",
                    String.format("Payment of %s failed for account %s. Payment ID: %s. Reason: %s",
                            amount, accountNumber, paymentId, reason != null ? reason : "Payment failed via payment gateway")
            );
            log.warn("Payment failed notification sent for account: {}, paymentId: {}", accountNumber, paymentId);
        } catch (Exception e) {
            log.error("Error sending payment failed notification: {}", e.getMessage());
        }
    }


    private void sendAlert(String accountNumber ,String Object,String message){

    }


}
