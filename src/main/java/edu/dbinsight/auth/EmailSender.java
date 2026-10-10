package edu.dbinsight.auth;

public interface EmailSender {
    void send(String from, String to, String subject, String text);
}
