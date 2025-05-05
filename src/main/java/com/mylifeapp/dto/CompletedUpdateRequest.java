package com.mylifeapp.dto;

public class CompletedUpdateRequest {
    private Boolean important;

    public Boolean getCompleted() {
        return important;
    }

    public void setCompleted(Boolean completed) {
        this.important = completed;
    }
}