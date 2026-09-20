package com.banking.transaction_service.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotBlank(message = "Sender account number is required")
    private String senderAccountNumber;
    @NotBlank(message = "receiver account number is required")
    private String receiverAccountNumber;
    @NotBlank(message = "amount is required")
    @Positive
    private BigDecimal amount;
    private String description;
}
