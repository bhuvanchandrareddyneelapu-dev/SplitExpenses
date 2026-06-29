package com.splitwisemoney.dto;

import java.math.BigDecimal;

public class ParticipantShareDto {
    private Long userId;
    private String fullName;
    private String email;
    private BigDecimal shareAmount;

    public ParticipantShareDto(Long userId, String fullName, String email, BigDecimal shareAmount) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.shareAmount = shareAmount;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public BigDecimal getShareAmount() { return shareAmount; }
    public void setShareAmount(BigDecimal shareAmount) { this.shareAmount = shareAmount; }
}
