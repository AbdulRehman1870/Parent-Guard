package com.example.parental_control;

public class GradioRequest {
    private String text;

    public GradioRequest(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
