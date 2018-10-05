package com.telenor.possumauth.interfaces;

public interface IAuthCompleted {
    void messageReturned(String message, String responseMessage, Exception e);
}