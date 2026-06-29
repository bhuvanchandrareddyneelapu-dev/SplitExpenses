package com.splitwisemoney.dto;

import java.time.LocalDateTime;

public class ActivityLogDto {
    private Long id;
    private String userName;
    private String action;
    private LocalDateTime createdAt;

    public ActivityLogDto(Long id, String userName, String action, LocalDateTime createdAt) {
        this.id = id;
        this.userName = userName;
        this.action = action;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
