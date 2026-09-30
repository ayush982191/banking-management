package com.banking.transaction_service.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Sender account number is required")
    @Pattern(regexp = "^[0-9]{10,20}$",
            message = "Sender account number must be 10-20 digits")
    @Column(nullable = false, length = 20)
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account number is required")
    @Pattern(regexp = "^[0-9]{10,20}$",
            message = "Receiver account number must be 10-20 digits")
    @Column(nullable = false, length = 20)
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000")
    @Digits(integer = 15, fraction = 2,
            message = "Amount must have up to 15 digits and 2 decimals")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @NotNull(message = "Transaction status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Column(length = 255)
    private String description;

    @Size(max = 500, message = "Failure reason cannot exceed 500 characters")
    @Column(length = 500)
    private String failureReason;

    @NotBlank(message = "Reference number is required")
    @Pattern(regexp = "^[A-Z0-9-]{8,30}$",
            message = "Reference number must be 8-30 alphanumeric/uppercase chars")
    @Column(nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @NotNull(message = "Created timestamp is required")
    @PastOrPresent(message = "Created timestamp cannot be in the future")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PastOrPresent(message = "Completed timestamp cannot be in the future")
    private LocalDateTime completedAt;


}
