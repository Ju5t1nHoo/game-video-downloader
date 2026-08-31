package com.gamevideodownloader.exception;

/** 客户端非法请求异常。 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
