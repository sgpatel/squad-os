package com.example.fraud;

import com.example.fraud.adapters.SpringAiLlmAdapter;
import com.example.fraud.plans.*;
import io.squados.annotation.*;
import io.squados.approval.*;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.delegate.*;
import io.squados.event.*;
import io.squados.improve.*;
import io.squados.llm.LlmPort;
import io.squados.security.*;
import io.squados.trace.*;
import io.squados.vote.*;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;

/**
 * FraudDetectionApp — Real-time payment fraud detection.
 * Uses ALL 15 SquadOS annotations in one production use case.
 */
@SpringBootApplication
@SquadApplication
public class FraudDetectionApp {

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(FraudDetectionApp.class, args);
    }

}