package com.trevorism.model

class Payment {
    String id
    String senderIdentityId
    String recipientIdentityId
    BigDecimal amount
    String currency
    String status
    boolean requireSenderApproval

}
