package com.photoloader.service.bean;

public enum Quality {
    LOW(0.2f), MEDIUM(0.6f), HIGH(1);

    private float value;

    public float getValue() {
        return value;
    }

    Quality(float val) {
        this.value = val;
    }
}
