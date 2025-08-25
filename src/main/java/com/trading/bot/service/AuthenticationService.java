package com.trading.bot.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
public class AuthenticationService {
    
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    public String createSignature(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to create HMAC signature", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    public String createQueryString(String... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be in key-value pairs");
        }
        
        StringBuilder queryString = new StringBuilder();
        for (int i = 0; i < params.length; i += 2) {
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(params[i]).append("=").append(params[i + 1]);
        }
        
        return queryString.toString();
    }
}