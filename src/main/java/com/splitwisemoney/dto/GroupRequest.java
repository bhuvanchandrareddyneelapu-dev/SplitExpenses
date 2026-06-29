package com.splitwisemoney.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(max = 100, message = "Group name must be at most 100 characters")
    private String groupName;

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}
