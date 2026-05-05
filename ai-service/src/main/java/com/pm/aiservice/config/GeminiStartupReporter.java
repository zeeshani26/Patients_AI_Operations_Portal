package com.pm.aiservice.config;

import com.pm.aiservice.service.GeminiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Logs Gemini configuration once after the context is ready (easy to grep in Docker logs).
 */
@Component
@Order(100)
public class GeminiStartupReporter implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(GeminiStartupReporter.class);

  private final GeminiChatService geminiChatService;

  public GeminiStartupReporter(GeminiChatService geminiChatService) {
    this.geminiChatService = geminiChatService;
  }

  @Override
  public void run(ApplicationArguments args) {
    boolean chat = geminiChatService.isConfigured();
    boolean augment = geminiChatService.isAugmentEnabled();
    log.warn(
        "[GEMINI] Startup: genericChatEnabled={}, augmentEnabled={} (grep logs for [GEMINI])",
        chat,
        augment);
  }
}
