package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private String keyId = "temp1" ;
    private String keySeceret = "hello";
    private final KafkaTemplate<String ,Object> kafkaTemplate;

    private static final String  PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String  PAYMENT_FAILED_TOPIC = "payment.failed";

    /**
     * create razor pay payment method.
     * 1. create order in razorpay
     * 2. save the payment. i.e record in db
     * 3. return order detail to frontend
     * 4. frontend shows razorpay checkout page.
     * 5. user pays
     * 6. razorpay call webhook
     *
     * @param request
     * @return
     */
    public PaymentOrderResponse createPaymentOrder(@Valid CreatePaymentRequest request) throws RazorpayException {
        log.info("Creating payment order for account : {} and amount {}",request.getAccountNumber(),request.getAmount());
        RazorpayClient razorpayClient = new RazorpayClient(
                keyId,keySeceret
        );
        // converted amount
        int convertedAmount = request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue()
                ;
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount",convertedAmount);
        orderRequest.put("currency","USE/IND");
        orderRequest.put("receipt","rcpt"+ UUID.randomUUID().toString());
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        log.info("razorpay order created : {} ",razorpayOrder.get("id").toString());

        // save payment record
        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());
        Payment savedPayment = paymentRepository.save(payment);
        return mapToResponse(savedPayment);
        
    }

    private PaymentOrderResponse mapToResponse(Payment payment) {
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setPaymentId(payment.getId());
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency() != null ? payment.getCurrency() : "INR");
        response.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        response.setRazorpayKeyId(keyId);
        return response;
    }

    public void handleWebHook(Map<String, Object> payload) {
        log.info("received razorpay webhook {}",payload.get("event"));
        String event = (String) payload.get("event");
        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        }else if("payment.failed".equals(event)){
            handlePaymentFailed(payload);
        }


    }

    private void handlePaymentFailed(Map<String, Object> payload) {
        try{
            Map<String ,Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()->new RuntimeException("Payment not found for order "+orderId));
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via razorpay");
            paymentRepository.save(payment);


            // publish payment event to kafka
            Map<String ,Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("reason",payment.getFailureReason());


            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId(),event);
            log.warn("Payment failed {} reason {}",payment.getId() ,payment.getFailureReason());


        } catch (Exception e) {
            log.error("Error handling payment {}",e.getMessage());
        }
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try{
            Map<String ,Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");
            String paymentId = (String) paymentData.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()->new RuntimeException("Payment not found for order "+orderId));

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // publish payment event to kafka
            Map<String ,Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("razorpayPaymentId",payment.getRazorpayPaymentId());

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId(),event);
            log.info("Payment completed {}",payment.getId());




        } catch (Exception e) {
            log.error("Error handling payment {}",e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String ,Object> entity = (Map<String, Object>) payload.get("payload");
        Map<String ,Object> paymentWrapper = (Map<String, Object>) entity.get("payment");

        return (Map<String, Object>) paymentWrapper.get("entity");


    }

}
