package com.example.demo;

public class Base62Encoder {
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }
}