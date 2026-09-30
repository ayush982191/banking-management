package com.banking.transaction_service.entity;


/**
 * Transaction Lifecycle flow
 *  pending -> processing -> completed (clean transaction)
 *  pending -> processing -> pending_verification (suspicious detected)
 *                         -> completed(verified)
 *                         -> flagged (SAGA refund)
 */
public enum TransactionStatus {
    PENDING, PROCESSING, PENDING_VERIFICATION,
    COMPLETED, FAILED, FLAGGED
}
