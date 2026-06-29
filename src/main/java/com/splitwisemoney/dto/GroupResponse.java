package com.splitwisemoney.dto;

import java.time.LocalDateTime;

public class GroupResponse {
    private Long id;
    private String groupName;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    public GroupResponse(Long id, String groupName, Long createdById, String createdByName, LocalDateTime createdAt) {
        this.id = id;
        this.groupName = groupName;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
