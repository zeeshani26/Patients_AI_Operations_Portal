package com.pm.patientservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("patients");
    cacheManager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats());
    return cacheManager;
  }
}


