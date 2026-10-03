package com.banking.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    private String paymentId;
    private String razorpayOrderId;


//    private String accountNumber;
    private BigDecimal amount;
    private String currency;
//    private String description;
    private String status;
    private String razorpayKeyId;

}
