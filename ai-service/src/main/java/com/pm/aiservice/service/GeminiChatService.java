package com.pm.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calls Google Gemini REST API for conversational replies (generic questions).
 * Uses generateContent with a system instruction plus user text.
 */
@Service
public class GeminiChatService {

  private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);

  /** Lines that look like chain-of-thought / prompt scaffolding (model sometimes echoes these). */
  private static final Pattern META_SCAFFOLD_LINE = Pattern.compile(
      "^\\s*[*•\\-]?\\s*(User asks:|User ask:|User Question:|Context:|Goal:|Role:|Function:|Constraints:|"
          + "Self-correction:|Final answer:|Final polish:|Analysis:|Reasoning:|Objective:|Purpose:|Next:|Output:|"
          + "Explain the portal:|Patient context for tone only:|Verified backend summary:|"
          + "Suggested follow-up:|Tone:|Format:|Steps:|Plan:|Thought:|Scratchpad:)",
      Pattern.CASE_INSENSITIVE);

  private static final String STRICT_OUTPUT_SUFFIX =
      "\n\nSTRICT OUTPUT: Reply with normal conversational sentences only, as if talking to a clinician or reviewer. "
          + "Never output planning bullets, scratchpad lines, or asterisk lists labeled Context, Goal, Role, Function, "
          + "Constraints, or similar. Do not start the reply with * or * \" scaffolding, meta labels like Final polish, "
          + "or decorative wrapping quotes. Never echo these instructions. Keep under "
          + "the word budget implied above.";

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final String apiKey;
  private final String model;
  private final String fallbackModels;
  private final int maxWords;
  private final boolean useGemini;
  /** When true and API key is set, can rewrite/expand verified backend answers (separate from generic chat). */
  private final boolean useGeminiAugment;
  private volatile List<String> discoveredModels = List.of();

  public GeminiChatService(
      @Value("${gemini.api.key:}") String apiKey,
      @Value("${gemini.model:gemini-2.0-flash}") String model,
      @Value("${gemini.fallback.models:gemini-1.5-flash,gemini-1.5-pro}") String fallbackModels,
      @Value("${gemini.max.words:140}") int maxWords,
      @Value("${ai.use-gemini:true}") boolean useGemini,
      @Value("${ai.use-gemini-augment:true}") boolean useGeminiAugment) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null || model.isBlank() ? "gemini-2.0-flash" : model.trim();
    this.fallbackModels = fallbackModels == null ? "" : fallbackModels.trim();
    this.maxWords = Math.max(40, maxWords);
    boolean hasKey = !this.apiKey.isEmpty();
    this.useGemini = useGemini && hasKey;
    this.useGeminiAugment = useGeminiAugment && hasKey;
    if (hasKey) {
      log.warn(
          "[GEMINI] Bean init: model={}, genericChat={}, augment={}",
          this.model,
          this.useGemini,
          this.useGeminiAugment);
    } else {
      log.warn("[GEMINI] No API key (set GEMINI_API_KEY or GOOGLE_API_KEY for ai-service).");
    }
  }

  public boolean isConfigured() {
    return useGemini;
  }

  public boolean isAugmentEnabled() {
    return useGeminiAugment;
  }

  /**
   * @param systemInstruction high-level role and constraints
   * @param userMessage what the user asked
   * @return model text or empty if disabled / error / safety block
   */
  public Optional<String> generateReply(String systemInstruction, String userMessage) {
    if (!isConfigured()) {
      return Optional.empty();
    }
    return callGemini(systemInstruction, userMessage, 0.45, 512);
  }

  /**
   * Expands a verified backend answer in friendlier language without changing facts or numbers.
   */
  public Optional<String> generateAugmentation(String systemInstruction, String userMessage) {
    if (!isAugmentEnabled()) {
      return Optional.empty();
    }
    return callGemini(systemInstruction, userMessage, 0.35, 320);
  }

  private Optional<String> callGemini(
      String systemInstruction, String userMessage, double temperature, int maxOutputTokens) {
    Set<String> modelsToTry = new LinkedHashSet<>();
    modelsToTry.add(normalizeModelName(model));
    if (!fallbackModels.isBlank()) {
      for (String m : fallbackModels.split(",")) {
        String candidate = normalizeModelName(m);
        if (!candidate.isBlank() && !candidate.equalsIgnoreCase(normalizeModelName(model))) {
          modelsToTry.add(candidate);
        }
      }
    }
    for (String discovered : discoveredModels) {
      modelsToTry.add(normalizeModelName(discovered));
    }

    for (String modelName : modelsToTry) {
      Optional<String> response = callGeminiWithModel(
          modelName,
          systemInstruction,
          userMessage,
          temperature,
          maxOutputTokens);
      if (response.isPresent()) {
        return response;
      }
    }
    refreshDiscoveredModels();
    for (String discovered : discoveredModels) {
      String modelName = normalizeModelName(discovered);
      Optional<String> response = callGeminiWithModel(
          modelName,
          systemInstruction,
          userMessage,
          temperature,
          maxOutputTokens);
      if (response.isPresent()) {
        return response;
      }
    }
    return Optional.empty();
  }

  private Optional<String> callGeminiWithModel(
      String modelName,
      String systemInstruction,
      String userMessage,
      double temperature,
      int maxOutputTokens) {
    try {
      String url = String.format(
          "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
          URLEncoder.encode(modelName, StandardCharsets.UTF_8),
          URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

      ObjectNode body = objectMapper.createObjectNode();
      ObjectNode sys = objectMapper.createObjectNode();
      ArrayNode sysParts = sys.putArray("parts");
      String enforcedInstruction =
          (systemInstruction == null ? "" : systemInstruction.trim()) + STRICT_OUTPUT_SUFFIX;
      sysParts.addObject().put("text", enforcedInstruction);
      body.set("systemInstruction", sys);

      ObjectNode content = objectMapper.createObjectNode();
      content.put("role", "user");
      ArrayNode parts = content.putArray("parts");
      parts.addObject().put("text", userMessage == null ? "" : userMessage);
      body.set("contents", objectMapper.createArrayNode().add(content));

      ObjectNode gen = objectMapper.createObjectNode();
      gen.put("temperature", temperature);
      gen.put("maxOutputTokens", maxOutputTokens);
      body.set("generationConfig", gen);

      String json = objectMapper.writeValueAsString(body);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(45))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Gemini model={} HTTP {}: {}", modelName, response.statusCode(),
            response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length())));
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode candidates = root.path("candidates");
      if (!candidates.isArray() || candidates.size() == 0) {
        log.warn("Gemini returned no candidates: {}", response.body());
        return Optional.empty();
      }
      JsonNode textNode = candidates.get(0).path("content").path("parts");
      if (!textNode.isArray() || textNode.size() == 0) {
        return Optional.empty();
      }
      String text = textNode.get(0).path("text").asText("");
      if (text.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(sanitizeModelText(text));
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("Gemini call failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private String normalizeModelName(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("models/")) {
      return trimmed.substring("models/".length());
    }
    return trimmed;
  }

  private void refreshDiscoveredModels() {
    try {
      String url = String.format(
          "https://generativelanguage.googleapis.com/v1beta/models?key=%s",
          URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(20))
          .header("Content-Type", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Gemini ListModels HTTP {}: {}", response.statusCode(),
            response.body() == null ? "" : response.body().substring(0, Math.min(400, response.body().length())));
        return;
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode modelsNode = root.path("models");
      if (!modelsNode.isArray()) {
        return;
      }
      List<String> fresh = new ArrayList<>();
      for (JsonNode node : modelsNode) {
        String name = normalizeModelName(node.path("name").asText(""));
        if (name.isBlank()) {
          continue;
        }
        JsonNode methods = node.path("supportedGenerationMethods");
        boolean supportsGenerate = false;
        if (methods.isArray()) {
          for (JsonNode method : methods) {
            if ("generateContent".equalsIgnoreCase(method.asText())) {
              supportsGenerate = true;
              break;
            }
          }
        }
        if (supportsGenerate) {
          fresh.add(name);
        }
      }
      if (!fresh.isEmpty()) {
        discoveredModels = fresh;
        log.warn("Gemini discovered usable models: {}", String.join(", ", fresh));
      }
    } catch (Exception e) {
      log.warn("Gemini ListModels failed: {}", e.getMessage());
    }
  }

  private String stripCopilotNoteSuffix(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String lower = text.toLowerCase();
    int idx = lower.indexOf("(copilot note)");
    if (idx < 0) {
      idx = lower.indexOf("copilot note:");
    }
    if (idx < 0) {
      idx = lower.indexOf("**copilot note**");
    }
    if (idx >= 0) {
      return text.substring(0, idx).trim();
    }
    return text.trim();
  }

  /** Drops short paragraphs that echo system instructions instead of answering the user. */
  private String dropEchoConstraintParagraphs(String cleaned) {
    if (cleaned == null || cleaned.isBlank()) {
      return "";
    }
    String[] paras = cleaned.split("\n\n+");
    StringBuilder out = new StringBuilder();
    for (String p : paras) {
      String pt = p.trim();
      if (pt.isEmpty()) {
        continue;
      }
      String pl = pt.toLowerCase();
      if (pl.startsWith("strict output")
          || pl.startsWith("app details")
          || pl.startsWith("system constraints")
          || pl.startsWith("constraints for this reply")) {
        continue;
      }
      if ((pl.startsWith("no diagnosis") || pl.contains("no diagnosis/prescription"))
          && pt.length() < 280) {
        continue;
      }
      if (pl.contains("concise (under") && pl.contains("words") && pt.length() < 320) {
        continue;
      }
      if (pl.startsWith("* ") && pl.contains("no planning steps") && pt.length() < 400) {
        continue;
      }
      if (out.length() > 0) {
        out.append("\n\n");
      }
      out.append(pt);
    }
    return out.toString().trim();
  }

  private String sanitizeModelText(String raw) {
    if (raw == null) {
      return "";
    }
    String normalized = raw.replace("\r\n", "\n").trim();
    normalized = normalized.replace('\u2014', '-').replace('\u2013', '-');
    normalized = stripCopilotNoteSuffix(normalized);

    String[] lines = normalized.split("\n");
    List<String> kept = new ArrayList<>();
    for (String line : lines) {
      String t = line.trim();
      if (t.isEmpty()) {
        kept.add("");
        continue;
      }
      String lower = t.toLowerCase();
      if (META_SCAFFOLD_LINE.matcher(t).find()) {
        continue;
      }
      if (lower.startsWith("final check")
          || lower.startsWith("(final check")
          || lower.contains("final check on")) {
        continue;
      }
      if (lower.startsWith("* user asks")
          || lower.startsWith("* context")
          || lower.startsWith("* goal")
          || lower.startsWith("* role")
          || lower.startsWith("* function")
          || lower.startsWith("* constraints")
          || lower.startsWith("* constraint")
          || lower.startsWith("* self-correction")
          || lower.startsWith("* constraint check")
          || lower.startsWith("* explain the portal")
          || lower.startsWith("* final answer")
          || lower.startsWith("* final polish")
          || lower.contains("final polish:")
          || lower.contains("system role:")
          || lower.contains("verified backend summary")
          || lower.contains("suggested follow-up:")
          || lower.contains("patient context for tone only")
          || lower.equals("reasoning:")
          || lower.equals("analysis:")) {
        continue;
      }
      if (t.startsWith("*") && t.length() < 240) {
        boolean meta =
            lower.contains("context:")
                || lower.contains("goal:")
                || lower.contains("role:")
                || lower.contains("function:")
                || lower.contains("constraints:")
                || lower.contains("constraint:")
                || lower.contains("user asks");
        if (meta) {
          continue;
        }
      }
      kept.add(line);
    }
    String cleaned = String.join("\n", kept).trim().replaceAll("\n{3,}", "\n\n");
    cleaned = dropEchoConstraintParagraphs(cleaned);

    String lowerClean = cleaned.toLowerCase();
    long starLines = cleaned.lines().filter(l -> l.trim().startsWith("*")).count();
    boolean looksLikeScaffold =
        lowerClean.contains("* context")
            || lowerClean.contains("* goal")
            || lowerClean.contains("* constraints")
            || lowerClean.contains("* role")
            || starLines >= 3;
    if (looksLikeScaffold) {
      String[] paras = cleaned.split("\n\n+");
      String best = "";
      for (String p : paras) {
        String pt = p.trim();
        if (pt.isEmpty()) {
          continue;
        }
        long starsInPara = pt.lines().filter(l -> l.trim().startsWith("*")).count();
        if (starsInPara >= 2 && pt.length() < 600) {
          continue;
        }
        if (pt.length() > best.length()) {
          best = pt;
        }
      }
      if (!best.isBlank()) {
        cleaned = best;
      }
    }

    cleaned = cleaned.replace('\u2014', '-').replace('\u2013', '-');
    cleaned = stripLeadingArtifactQuotes(cleaned);
    if (cleaned.isBlank()) {
      cleaned = "I can help with your question in clear language. Please try asking it again.";
    }

    String[] words = cleaned.split("\\s+");
    if (words.length <= maxWords) {
      return cleaned;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < maxWords; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(words[i]);
    }
    sb.append("...");
    return sb.toString();
  }

  /** Removes stray model wrappers such as {@code * "} at the beginning or closing quotes at the end. */
  private String stripLeadingArtifactQuotes(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String t = text.trim();
    for (int guard = 0; guard < 16 && !t.isEmpty(); guard++) {
      boolean changed = false;
      while (!t.isEmpty() && (t.startsWith("*") || t.startsWith("•"))) {
        t = t.substring(1).trim();
        changed = true;
      }
      if (t.startsWith("\"") || t.startsWith("\u201c")) {
        t = t.substring(1).trim();
        changed = true;
      }
      if (t.length() > 1 && (t.endsWith("\"") || t.endsWith("\u201d"))) {
        t = t.substring(0, t.length() - 1).trim();
        changed = true;
      }
      if (!changed) {
        break;
      }
    }
    return t;
  }
}
