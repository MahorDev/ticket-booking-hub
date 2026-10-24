package com.tickethub.event.service;

public interface StorageService {

    String upload(String directory, String filename, byte[] content, String contentType);

    byte[] download(String key);

    void delete(String key);
}
