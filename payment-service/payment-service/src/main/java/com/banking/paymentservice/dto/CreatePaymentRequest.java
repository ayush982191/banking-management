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
public class CreatePaymentRequest {
    @NotNull(message = "account no is required")
    private String accountNumber;
    @NotNull(message = "account no is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    private String description;
}
