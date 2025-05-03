package com.example.parental_control; // Use your package name

public class IntroSlideItem {
    private String title;
    private String description;
    private int imageResource;

    public IntroSlideItem(String title, String description, int imageResource) {
        this.title = title;
        this.description = description;
        this.imageResource = imageResource;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getImageResource() {
        return imageResource;
    }
}