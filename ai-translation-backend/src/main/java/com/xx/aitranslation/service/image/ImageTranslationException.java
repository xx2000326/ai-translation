package com.xx.aitranslation.service.image;

public class ImageTranslationException extends RuntimeException {
    public ImageTranslationException(String message) {
        super(message);
    }

    public ImageTranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
