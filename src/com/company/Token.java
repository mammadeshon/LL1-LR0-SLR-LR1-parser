package com.company;

public class Token {
    String token;

    public Token(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(" ").append(token).append(" ");
        return builder.toString();
    }
}
