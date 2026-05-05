(function () {
  const $ = (id) => document.getElementById(id);
  const elements = {
    authShell: $("authShell"),
    loginCard: $("loginCard"),
    registerCard: $("registerCard"),
    showLoginBtn: $("showLoginBtn"),
    showRegisterBtn: $("showRegisterBtn"),
    appContent: $("appContent"),
    sessionUser: $("sessionUser"),
    logoutBtn: $("logoutBtn"),
    activePatientBadge: $("activePatientBadge"),
    authFeedback: $("authFeedback"),
    registerFeedback: $("registerFeedback"),
    navButtons: document.querySelectorAll(".nav-btn"),
    routes: document.querySelectorAll(".route"),
    baseUrl: $("baseUrl"),
    jwtToken: $("jwtToken"),
    loginEmail: $("loginEmail"),
    loginPassword: $("loginPassword"),
    registerEmail: $("registerEmail"),
    registerPassword: $("registerPassword"),
    responseOutput: $("responseOutput"),
    statusBadge: $("statusBadge"),
    activityLog: $("activityLog"),
    resultSummary: $("resultSummary"),
    meaningPanel: $("meaningPanel"),
    kpiCards: $("kpiCards"),
    analyticsCards: $("analyticsCards"),
    analyticsChart: $("analyticsChart"),
    clinicalInsights: $("clinicalInsights"),
    riskFactorsChart: $("riskFactorsChart"),
    riskContributionTable: $("riskContributionTable"),
    patientsTable: $("patientsTable"),
    predictionsTable: $("predictionsTable"),
    guardrailCards: $("guardrailCards"),
    experimentCards: $("experimentCards"),
    comparisonChart: $("comparisonChart"),
    experimentTrendChart: $("experimentTrendChart"),
    findingsSummary: $("findingsSummary"),
    findingsTable: $("findingsTable"),
    proposalChecklist: $("proposalChecklist"),
    featureGuide: $("featureGuide"),
    chatThread: $("chatThread"),
    chatThinkingBar: $("chatThinkingBar"),
    chatInput: $("chatInput"),
    chatContext: $("chatContext"),
    chatbotWidget: $("chatbotWidget"),
    chatPanel: $("chatPanel"),
    chatToggleBtn: $("chatToggleBtn"),
    chatCloseBtn: $("chatCloseBtn"),
    chatExpandBtn: $("chatExpandBtn"),
  };

  /** When the portal is opened by IP or hostname (not localhost), API calls must use that origin — not localhost. */
  function inferGatewayBaseUrl() {
    const stored = localStorage.getItem("pm.baseUrl");
    const origin = window.location.origin;
    const host = window.location.hostname;
    const inputFallback = elements.baseUrl ? elements.baseUrl.value.trim() : "";
    if (host !== "localhost" && host !== "127.0.0.1") {
      if (stored && !/localhost|127\.0\.0\.1/i.test(stored)) {
        return stored;
      }
      return origin;
    }
    return stored || inputFallback || "http://localhost:4004";
  }

  const state = {
    baseUrl: inferGatewayBaseUrl(),
    token: localStorage.getItem("pm.token") || "",
    patientProfiles: JSON.parse(localStorage.getItem("pm.patientProfiles") || "{}"),
    currentRoute: "dashboard",
    latestPayload: null,
    latestPatients: [],
    selectedPatientIds: new Set(),
    protocolId: "",
    protocolLabel: "",
    lastChaosInputs: null,
    findings: {
      f1: { runs: 0, examples: [] },
      f2: { runs: 0, deltas: [] },
      f3: { runs: 0, strategies: [] },
      f4: { suites: 0, totalRuns: 0 },
      f5: { prompts: 0, failures: 0 },
    },
    latestGuardrail: null,
    latestScenarioSuite: null,
  };
  const validRoutes = new Set([
    "dashboard", "patients", "analytics", "digitalTwin", "predictions",
    "chaos", "experiments", "findings", "reports", "settings"
  ]);

  function init() {
    elements.baseUrl.value = state.baseUrl;
    elements.jwtToken.value = state.token;
    if ($("patientRegisteredDate") && !$("patientRegisteredDate").value) {
      $("patientRegisteredDate").value = new Date().toISOString().slice(0, 10);
    }
    bindActions();
    bindAuthMode();
    bindNavigation();
    bindRouteSync();
    syncAuthGate();
    renderStatus("Ready", "neutral");
    renderResponse({ message: "Portal initialized. Sign in to continue." });
    renderDashboardCards();
    renderFeatureGuide();
    renderSectionHelp();
    renderProposalChecklist();
    renderFindings();
    updateChatContext();
    pushChat(
      "assistant",
      "Hi, I am your AI copilot. Ask about risk, anomalies, interventions, or definitions (for example, what is a guardrail or Chaos Center)."
    );
  }

  function bindAuthMode() {
    if (!elements.showLoginBtn || !elements.showRegisterBtn) return;
    const setMode = (mode) => {
      const isLogin = mode === "login";
      elements.loginCard.hidden = !isLogin;
      elements.registerCard.hidden = isLogin;
      elements.loginCard.classList.toggle("active", isLogin);
      elements.registerCard.classList.toggle("active", !isLogin);
      elements.showLoginBtn.classList.toggle("active", isLogin);
      elements.showRegisterBtn.classList.toggle("active", !isLogin);
      if (isLogin) {
        if (elements.registerFeedback) {
          elements.registerFeedback.hidden = true;
          elements.registerFeedback.textContent = "";
          elements.registerFeedback.classList.remove("success");
        }
      } else if (elements.authFeedback) {
        elements.authFeedback.hidden = true;
        elements.authFeedback.textContent = "";
        elements.authFeedback.classList.remove("success");
      }
    };
    elements.showLoginBtn.addEventListener("click", () => setMode("login"));
    elements.showRegisterBtn.addEventListener("click", () => setMode("register"));
    setMode("login");
  }

  function clearAuthFeedback() {
    if (elements.authFeedback) {
      elements.authFeedback.hidden = true;
      elements.authFeedback.textContent = "";
      elements.authFeedback.classList.remove("success");
    }
    if (elements.registerFeedback) {
      elements.registerFeedback.hidden = true;
      elements.registerFeedback.textContent = "";
      elements.registerFeedback.classList.remove("success");
    }
  }

  function showAuthFeedback(message, type, isSuccess = false) {
    const target = type === "register" ? elements.registerFeedback : elements.authFeedback;
    if (!target) return;
    target.textContent = message;
    target.hidden = false;
    target.classList.toggle("success", Boolean(isSuccess));
  }

  function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || "").trim());
  }

  function validatePassword(password) {
    const value = String(password || "");
    if (value.length < 8) {
      return "Password must be at least 8 characters.";
    }
    return "";
  }

  function mapAuthError(errorMessage) {
    const text = String(errorMessage || "").toLowerCase();
    if (text.includes("401")) {
      return "Invalid email or password. Please check and try again.";
    }
    if (text.includes("409") || text.includes("already exists")) {
      return "This email is already registered. Try signing in instead.";
    }
    if (text.includes("400")) {
      return "Invalid request. Please check email/password format and try again.";
    }
    return "Authentication failed. Please try again in a few seconds.";
  }

  function bindNavigation() {
    elements.navButtons.forEach((button) => {
      button.addEventListener("click", () => {
        if (!isAuthenticated()) {
          renderStatus("Sign in required", "error");
          return;
        }
        setRoute(button.dataset.route, true);
      });
    });
  }

  function bindRouteSync() {
    window.addEventListener("hashchange", () => {
      const route = routeFromHash();
      if (isAuthenticated()) {
        setRoute(route, false);
      }
    });
  }

  function routeFromHash() {
    const raw = window.location.hash || "#/dashboard";
    const normalized = raw.replace(/^#\//, "");
    return validRoutes.has(normalized) ? normalized : "dashboard";
  }

  function bindActions() {
    $("saveConfigBtn").addEventListener("click", () => {
      state.baseUrl = elements.baseUrl.value.trim();
      state.token = elements.jwtToken.value.trim();
      persistState();
      renderStatus("Settings saved", "success");
      log("Settings saved");
    });

    $("clearTokenBtn").addEventListener("click", logout);
    $("logoutBtn").addEventListener("click", logout);
    $("loginBtn").addEventListener("click", login);
    $("registerBtn").addEventListener("click", register);
    $("createPatientBtn").addEventListener("click", createPatient);
    $("listPatientsBtn").addEventListener("click", listPatients);
    $("updatePatientBtn").addEventListener("click", updatePatient);
    $("deletePatientBtn").addEventListener("click", deletePatient);
    $("deleteSelectedPatientsBtn").addEventListener("click", deleteSelectedPatients);
    $("importDatasetPatientsBtn").addEventListener("click", importDatasetPatients);
    $("analyticsBtn").addEventListener("click", getAnalytics);
    $("assessRiskBtn").addEventListener("click", assessRisk);
    $("detectAnomaliesBtn").addEventListener("click", detectAnomalies);
    $("analyzeInterventionBtn").addEventListener("click", analyzeIntervention);
    $("predictionsBtn").addEventListener("click", getPredictions);
    $("guardrailDecisionBtn").addEventListener("click", evaluateGuardrailDecision);
    $("presetNormalBtn").addEventListener("click", () => applyChaosPreset("normal"));
    $("presetModerateBtn").addEventListener("click", () => applyChaosPreset("moderate"));
    $("presetSevereBtn").addEventListener("click", () => applyChaosPreset("severe"));
    $("replayChaosBtn").addEventListener("click", replayLastChaosRun);
    $("modelComparisonBtn").addEventListener("click", getModelComparison);
    $("startProtocolBtn").addEventListener("click", startProtocol);
    $("logExperimentBtn").addEventListener("click", logExperimentRun);
    $("experimentSummaryBtn").addEventListener("click", getExperimentSummary);
    $("runScenarioSuiteBtn").addEventListener("click", runScenarioSuite);
    $("refreshFindingsBtn").addEventListener("click", renderFindings);
    $("exportFindingsMdBtn").addEventListener("click", exportFindingsMarkdown);
    $("exportFindingsCsvBtn").addEventListener("click", exportFindingsCsv);
    $("exportProtocolReportBtn").addEventListener("click", exportProtocolReport);
    $("chatSendBtn").addEventListener("click", handleAssistantChat);
    $("chatToggleBtn").addEventListener("click", () => {
      openChatPanel();
    });
    $("chatCloseBtn").addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      closeChatPanel();
    });
    if (elements.chatExpandBtn) {
      $("chatExpandBtn").addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        elements.chatPanel.classList.toggle("fullscreen");
      });
    }
    elements.chatInput.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" || event.shiftKey) return;
      event.preventDefault();
      handleAssistantChat();
    });
    window.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && isChatOpen()) {
        closeChatPanel();
      }
    });
  }

  function isChatOpen() {
    return !elements.chatPanel.hidden && elements.chatPanel.classList.contains("is-open");
  }

  function openChatPanel() {
    if (isChatOpen()) return;
    elements.chatPanel.hidden = false;
    elements.chatPanel.style.display = "grid";
    elements.chatPanel.classList.remove("is-closing");
    requestAnimationFrame(() => {
      elements.chatPanel.classList.add("is-open");
    });
  }

  function closeChatPanel() {
    if (elements.chatPanel.hidden) return;
    elements.chatPanel.classList.remove("is-open");
    elements.chatPanel.classList.add("is-closing");
    window.setTimeout(() => {
      elements.chatPanel.hidden = true;
      elements.chatPanel.style.display = "none";
      elements.chatPanel.classList.remove("is-closing");
      elements.chatPanel.classList.remove("fullscreen");
    }, 240);
  }

  function persistState() {
    localStorage.setItem("pm.baseUrl", state.baseUrl);
    localStorage.setItem("pm.token", state.token);
    localStorage.setItem("pm.patientProfiles", JSON.stringify(state.patientProfiles || {}));
  }

  function isAuthenticated() {
    return Boolean((elements.jwtToken.value || state.token || "").trim());
  }

  function decodeJwtSubject(token) {
    try {
      const parts = String(token || "").split(".");
      if (parts.length < 2) return "";
      const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
      const padded = payload + "=".repeat((4 - (payload.length % 4)) % 4);
      const json = JSON.parse(atob(padded));
      return String(json.sub || "").trim();
    } catch (error) {
      return "";
    }
  }

  function updateSessionUserLabel() {
    if (!elements.sessionUser) return;
    const token = (elements.jwtToken.value || state.token || "").trim();
    const email = elements.loginEmail?.value?.trim() || decodeJwtSubject(token);
    const authenticated = Boolean(token);
    elements.sessionUser.hidden = !authenticated;
    elements.sessionUser.textContent = authenticated ? `Signed in: ${email || "user"}` : "";
  }

  function syncAuthGate() {
    const authenticated = isAuthenticated();
    elements.authShell.hidden = authenticated;
    elements.authShell.style.display = authenticated ? "none" : "flex";
    elements.appContent.hidden = !authenticated;
    elements.appContent.style.display = authenticated ? "block" : "none";
    elements.logoutBtn.hidden = !authenticated;
    updateSessionUserLabel();
    elements.chatbotWidget.hidden = !authenticated;
    elements.chatbotWidget.style.display = authenticated ? "" : "none";
    if (!authenticated) {
      closeChatPanel();
    }
    if (authenticated) {
      const route = routeFromHash();
      setRoute(route || state.currentRoute || "dashboard", false);
    }
  }

  function setRoute(route, updateHash) {
    const normalizedRoute = validRoutes.has(route) ? route : "dashboard";
    if (updateHash) {
      window.location.hash = `/${normalizedRoute}`;
    }
    state.currentRoute = normalizedRoute;
    elements.navButtons.forEach((button) => {
      button.classList.toggle("active", button.dataset.route === normalizedRoute);
    });
    elements.routes.forEach((panel) => {
      const active = panel.dataset.route === normalizedRoute;
      panel.hidden = !active;
      panel.classList.toggle("active", active);
    });
    if (normalizedRoute === "reports" && elements.meaningPanel && elements.resultSummary) {
      elements.meaningPanel.innerHTML = elements.resultSummary.innerHTML;
    }
    log(`Navigated to ${normalizedRoute}`);
  }

  function log(message) {
    const li = document.createElement("li");
    li.textContent = `${new Date().toLocaleTimeString()} - ${message}`;
    elements.activityLog.prepend(li);
    while (elements.activityLog.childElementCount > 100) {
      elements.activityLog.removeChild(elements.activityLog.lastChild);
    }
  }

  function renderStatus(label, kind) {
    elements.statusBadge.textContent = label;
    elements.statusBadge.className = `status ${kind}`;
  }

  function renderResponse(payload) {
    state.latestPayload = payload;
    elements.responseOutput.textContent = JSON.stringify(payload, null, 2);
  }

  function renderMeaning(html) {
    elements.resultSummary.innerHTML = html;
    if (state.currentRoute === "reports" && elements.meaningPanel) {
      elements.meaningPanel.innerHTML = html;
    }
  }

  function renderCards(container, cards) {
    if (!container) return;
    container.innerHTML = cards
      .map((card) => `<div class="kpi-card"><h4>${card.label}</h4><p>${card.value}</p></div>`)
      .join("");
  }

  function renderBarChart(container, title, items, unit) {
    if (!container) return;
    if (!items || items.length === 0) {
      container.innerHTML = "<p class='hint'>No chart data available.</p>";
      return;
    }
    const max = Math.max(...items.map((item) => Number(item.value) || 0), 1);
    const rows = items.map((item) => {
      const value = Number(item.value) || 0;
      const pct = Math.max(2, Math.round((value / max) * 100));
      return `<div class="bar-row">
        <div class="bar-label">${item.label}</div>
        <div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div>
        <div class="bar-value">${value}${unit || ""}</div>
      </div>`;
    }).join("");
    container.innerHTML = `<h4 class="chart-title">${title}</h4><div class="bar-chart">${rows}</div>`;
  }

  function renderProposalChecklist() {
    if (!elements.proposalChecklist) return;
    elements.proposalChecklist.innerHTML = `
      <ul>
        <li><strong>Original Question:</strong> Does causally-informed guardrail policy improve reliability while preserving risk-estimation fidelity under faults?</li>
        <li><strong>Counterfactual Contribution:</strong> Digital Twin + intervention analysis API demonstrates what-if reasoning.</li>
        <li><strong>Resilience Contribution:</strong> Chaos Center + causal guardrail decision endpoint demonstrates adaptive fallback logic.</li>
        <li><strong>Evaluation Contribution:</strong> Experiments page logs comparative metrics for Rule-based, LLM-only, and Causal-Hybrid modes.</li>
        <li><strong>Demonstrability:</strong> End-to-end UI workflows map directly to backend APIs and evidence records.</li>
      </ul>`;
  }

  function renderFeatureGuide() {
    if (!elements.featureGuide) return;
    elements.featureGuide.innerHTML = `
      <h4 class="chart-title">How to use this portal</h4>
      <ul>
        <li><strong>Patient Records:</strong> manage patients and click a row to set active chatbot context.</li>
        <li><strong>Analytics:</strong> inspect operational KPIs and distribution visuals.</li>
        <li><strong>Digital Twin:</strong> run risk, anomaly, and intervention what-if analysis.</li>
        <li><strong>Predictions:</strong> review historical AI outputs per patient.</li>
        <li><strong>Chaos Center:</strong> simulate incidents and inspect guardrail strategy switches.</li>
        <li><strong>Experiments:</strong> compare model behavior and log thesis evidence.</li>
      </ul>`;
  }

  function renderSectionHelp() {
    const routeInstructions = {
      dashboard: "Start here for guided workflow. Follow sequence: Patients -> Digital Twin -> Chaos -> Experiments -> Reports to build complete findings evidence.",
      patients: "Create or import patients, click rows to set active context, and use multi-select checkboxes plus Delete Selected for bulk cleanup.",
      analytics: "Load service metrics and review KPI cards + chart to explain operational trends in your report.",
      digitalTwin: "Run Assess Risk first, then Detect Anomalies, then Analyze Intervention. Compare outputs for the same patient.",
      predictions: "Enter patient ID and optional prediction type to view historical outputs for that patient.",
      chaos: "Input latency/error/load values and evaluate guardrail decisions across normal, moderate, and severe profiles.",
      experiments: "Run scenario suite, load summary, compare models, and log experiment rows for thesis findings.",
      findings: "Refresh to auto-build F1-F5 from your runs. Export Markdown/CSV to paste directly into your report appendix.",
      reports: "Read interpreted summaries and checklist alignment. Capture report-ready screenshots from this section.",
      settings: "Set gateway URL and JWT only when needed for manual troubleshooting."
    };

    elements.routes.forEach((panel) => {
      const route = panel.dataset.route;
      const text = routeInstructions[route];
      if (!text) return;
      const h2 = panel.querySelector("h2");
      if (!h2 || h2.querySelector(".help-icon")) return;
      const title = h2.textContent;
      h2.textContent = "";
      const wrapper = document.createElement("span");
      wrapper.className = "section-title";
      wrapper.innerHTML = `${title} <span class="help-icon" tabindex="0" aria-label="Section instructions">i<span class="help-tooltip">${text}</span></span>`;
      h2.appendChild(wrapper);
    });
  }

  function renderTable(container, rows, columns) {
    if (!container) return;
    if (!rows || rows.length === 0) {
      container.innerHTML = "<p class='hint'>No data available.</p>";
      return;
    }
    const header = columns.map((col) => `<th>${col.label}</th>`).join("");
    const body = rows
      .map((row) => `<tr>${columns.map((col) => `<td>${row[col.key] ?? ""}</td>`).join("")}</tr>`)
      .join("");
    container.innerHTML = `<table class="data-table"><thead><tr>${header}</tr></thead><tbody>${body}</tbody></table>`;
  }

  function renderPatientsTable(rows) {
    state.latestPatients = Array.isArray(rows) ? rows : [];
    const validIds = new Set(state.latestPatients.map((row) => String(row.id || "")));
    state.selectedPatientIds = new Set(
      [...state.selectedPatientIds].filter((id) => validIds.has(id))
    );
    if (!elements.patientsTable) return;
    if (!state.latestPatients.length) {
      elements.patientsTable.innerHTML = "<p class='hint'>No data available.</p>";
      return;
    }
    const body = state.latestPatients.map((row, idx) => {
      const id = String(row.id || "");
      const checked = state.selectedPatientIds.has(id) ? "checked" : "";
      return (
      `<tr class="patient-row" data-patient-index="${idx}">
        <td><input class="row-checkbox patient-select" type="checkbox" data-patient-id="${id}" ${checked}></td>
        <td>${row.id ?? ""}</td>
        <td>${row.name ?? ""}</td>
        <td>${row.email ?? ""}</td>
        <td>${row.address ?? ""}</td>
      </tr>`
      );
    }).join("");

    elements.patientsTable.innerHTML = `
      <p class='hint'>Tip: click a patient row to switch chatbot working context.</p>
      <table class="data-table">
        <thead><tr><th><input id="patientsSelectAll" class="row-checkbox" type="checkbox"></th><th>ID</th><th>Name</th><th>Email</th><th>Address</th></tr></thead>
        <tbody>${body}</tbody>
      </table>`;

    const setSelectAllState = () => {
      const all = elements.patientsTable.querySelectorAll(".patient-select");
      const selected = elements.patientsTable.querySelectorAll(".patient-select:checked");
      const selectAll = $("patientsSelectAll");
      if (!selectAll) return;
      if (!all.length) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
        return;
      }
      selectAll.checked = selected.length === all.length;
      selectAll.indeterminate = selected.length > 0 && selected.length < all.length;
    };

    const selectAll = $("patientsSelectAll");
    if (selectAll) {
      selectAll.addEventListener("change", () => {
        elements.patientsTable.querySelectorAll(".patient-select").forEach((cb) => {
          cb.checked = selectAll.checked;
          const id = cb.dataset.patientId;
          if (selectAll.checked) state.selectedPatientIds.add(id);
          else state.selectedPatientIds.delete(id);
        });
        setSelectAllState();
      });
    }

    elements.patientsTable.querySelectorAll(".patient-select").forEach((cb) => {
      cb.addEventListener("change", () => {
        const id = cb.dataset.patientId;
        if (cb.checked) state.selectedPatientIds.add(id);
        else state.selectedPatientIds.delete(id);
        setSelectAllState();
      });
    });

    elements.patientsTable.querySelectorAll(".patient-row").forEach((rowEl) => {
      rowEl.addEventListener("click", (event) => {
        if (event.target && event.target.classList.contains("patient-select")) return;
        const idx = Number(rowEl.dataset.patientIndex);
        const selected = state.latestPatients[idx];
        syncAiFromPatient(selected);
        renderMeaning(`<p>Working context switched to <strong>${selected.name}</strong> (${selected.id}).</p>`);
      });
    });
    setSelectAllState();
  }

  function renderDashboardCards() {
    renderCards(elements.kpiCards, [
      { label: "Workflow", value: "Clinical + AI + Resilience" },
      { label: "AI Modes", value: "Rule / LLM / Causal-Hybrid" },
      { label: "Demonstration", value: "Live and interactive" },
      { label: "Evidence", value: "Experiment logging enabled" },
    ]);
  }

  function startProtocol() {
    const rawProtocolId = ($("protocolId")?.value || "").trim();
    const rawLabel = ($("protocolLabel")?.value || "").trim();
    const generated = `PT-${new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 12)}`;
    state.protocolId = rawProtocolId || generated;
    state.protocolLabel = rawLabel || "General protocol";
    if ($("protocolId")) $("protocolId").value = state.protocolId;
    renderMeaning(`<p>Protocol started: <strong>${state.protocolId}</strong> (${state.protocolLabel}). New experiment logs will include this metadata.</p>`);
    log(`Protocol started: ${state.protocolId}`);
  }

  function captureCurrentChaosInputs() {
    return {
      aiLatencyMs: Number($("chaosAiLatency").value || 0),
      dbLatencyMs: Number($("chaosDbLatency").value || 0),
      kafkaLag: Number($("chaosKafkaLag").value || 0),
      errorRatePct: Number($("chaosErrorRate").value || 0),
      trafficLoadPct: Number($("chaosTrafficLoad").value || 0),
    };
  }

  function applyChaosPreset(level) {
    const presets = {
      normal: { aiLatencyMs: 120, dbLatencyMs: 90, kafkaLag: 500, errorRatePct: 0.8, trafficLoadPct: 35 },
      moderate: { aiLatencyMs: 350, dbLatencyMs: 280, kafkaLag: 6000, errorRatePct: 4.5, trafficLoadPct: 78 },
      severe: { aiLatencyMs: 900, dbLatencyMs: 850, kafkaLag: 22000, errorRatePct: 12.5, trafficLoadPct: 96 },
    };
    const preset = presets[level] || presets.moderate;
    $("chaosAiLatency").value = preset.aiLatencyMs;
    $("chaosDbLatency").value = preset.dbLatencyMs;
    $("chaosKafkaLag").value = preset.kafkaLag;
    $("chaosErrorRate").value = preset.errorRatePct;
    $("chaosTrafficLoad").value = preset.trafficLoadPct;
    state.lastChaosInputs = preset;
    renderMeaning(`<p>Chaos preset applied: <strong>${level.toUpperCase()}</strong>. Click Evaluate Guardrail Decision to run.</p>`);
  }

  function replayLastChaosRun() {
    if (!state.lastChaosInputs) {
      renderMeaning("<p>No previous chaos input captured yet. Run or apply a preset first.</p>");
      return;
    }
    $("chaosAiLatency").value = state.lastChaosInputs.aiLatencyMs;
    $("chaosDbLatency").value = state.lastChaosInputs.dbLatencyMs;
    $("chaosKafkaLag").value = state.lastChaosInputs.kafkaLag;
    $("chaosErrorRate").value = state.lastChaosInputs.errorRatePct;
    $("chaosTrafficLoad").value = state.lastChaosInputs.trafficLoadPct;
    evaluateGuardrailDecision();
  }

  function findingsRows() {
    const f1 = state.findings.f1;
    const f2 = state.findings.f2;
    const f3 = state.findings.f3;
    const f4 = state.findings.f4;
    const f5 = state.findings.f5;
    return [
      {
        id: "F1",
        question: "Explainability quality",
        evidence: `${f1.runs} risk runs`,
        result: f1.runs ? `Captured factor + confidence details. Latest: ${f1.examples[f1.examples.length - 1] || "n/a"}` : "Not enough data yet",
      },
      {
        id: "F2",
        question: "Intervention effect",
        evidence: `${f2.runs} intervention runs`,
        result: f2.runs ? `Delta range: ${Math.min(...f2.deltas).toFixed(3)} to ${Math.max(...f2.deltas).toFixed(3)}` : "Not enough data yet",
      },
      {
        id: "F3",
        question: "Guardrail adaptation",
        evidence: `${f3.runs} chaos decisions`,
        result: f3.runs ? `Observed strategies: ${[...new Set(f3.strategies)].join(", ")}` : "Not enough data yet",
      },
      {
        id: "F4",
        question: "Repeatability",
        evidence: `${f4.suites} suite runs`,
        result: f4.suites ? `Latest summary total runs: ${f4.totalRuns}` : "Not enough data yet",
      },
      {
        id: "F5",
        question: "Usability",
        evidence: `${f5.prompts} chat prompts`,
        result: f5.prompts ? `Failures: ${f5.failures}; success rate ${(100 * (f5.prompts - f5.failures) / f5.prompts).toFixed(1)}%` : "Not enough data yet",
      },
    ];
  }

  function renderFindings() {
    const rows = findingsRows();
    renderTable(elements.findingsTable, rows, [
      { key: "id", label: "Finding ID" },
      { key: "question", label: "Question" },
      { key: "evidence", label: "Evidence" },
      { key: "result", label: "Result" },
    ]);
    const completed = rows.filter((row) => !row.result.includes("Not enough")).length;
    if (elements.findingsSummary) {
      elements.findingsSummary.innerHTML =
        `<p>Findings readiness: <strong>${completed}/5</strong>. ` +
        `Use Refresh after running additional workflows, then export to appendices.</p>`;
    }
  }

  function exportTextFile(filename, content, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  function exportFindingsMarkdown() {
    const rows = findingsRows();
    const lines = [
      "# Findings Table",
      "",
      "| Finding ID | Question | Evidence | Result |",
      "|---|---|---|---|",
      ...rows.map((r) => `| ${r.id} | ${r.question} | ${r.evidence} | ${r.result} |`),
      "",
    ];
    exportTextFile("findings-table.md", lines.join("\n"), "text/markdown");
  }

  function exportFindingsCsv() {
    const rows = findingsRows();
    const escapeCsv = (value) => `"${String(value || "").replace(/"/g, "\"\"")}"`;
    const lines = [
      ["Finding ID", "Question", "Evidence", "Result"].map(escapeCsv).join(","),
      ...rows.map((r) => [r.id, r.question, r.evidence, r.result].map(escapeCsv).join(",")),
    ];
    exportTextFile("findings-table.csv", lines.join("\n"), "text/csv");
  }

  function exportProtocolReport() {
    const now = new Date();
    const protocolId = (($("protocolId")?.value || state.protocolId || "").trim()) || "UNSPECIFIED";
    const protocolLabel = (($("protocolLabel")?.value || state.protocolLabel || "").trim()) || "General protocol";
    const rows = findingsRows();
    const findingsTable = [
      "| Finding ID | Question | Evidence | Result |",
      "|---|---|---|---|",
      ...rows.map((r) => `| ${r.id} | ${r.question} | ${r.evidence} | ${r.result} |`),
    ].join("\n");

    const guardrail = state.latestGuardrail
      ? `- Suspected cause: ${state.latestGuardrail.suspectedCause || "N/A"}\n- Selected strategy: ${state.latestGuardrail.selectedStrategy || "N/A"}\n- Model mode: ${state.latestGuardrail.selectedModelMode || "N/A"}\n- Confidence: ${state.latestGuardrail.confidenceScore ?? "N/A"}`
      : "- No guardrail run captured yet.";

    const suite = state.latestScenarioSuite
      ? `- Executed runs: ${state.latestScenarioSuite.executedRuns || 0}\n- Strategies: ${Object.entries(state.latestScenarioSuite.guardrailStrategiesByScenario || {}).map((e) => `${e[0]} -> ${e[1]}`).join("; ") || "none"}`
      : "- No scenario suite run captured yet.";

    const content = [
      "# Protocol Report Appendix",
      "",
      `- Generated at: ${now.toISOString()}`,
      `- Protocol ID: ${protocolId}`,
      `- Protocol Label: ${protocolLabel}`,
      "",
      "## Findings Snapshot",
      findingsTable,
      "",
      "## Latest Guardrail Evidence",
      guardrail,
      "",
      "## Latest Scenario Suite Evidence",
      suite,
      "",
      "## Notes",
      "- This export is generated from the current UI session state.",
      "- Re-run key workflows before export if you need updated evidence.",
      "",
    ].join("\n");

    exportTextFile(`protocol-report-${protocolId}.md`, content, "text/markdown");
  }

  async function request(path, options = {}, requiresAuth = true) {
    const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
    if (requiresAuth) {
      const token = elements.jwtToken.value.trim() || state.token;
      if (!token) throw new Error("JWT token is required.");
      headers.Authorization = `Bearer ${token}`;
    }
    const url = `${state.baseUrl}${path}`;
    const response = await fetch(url, Object.assign({}, options, { headers }));
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : await response.text();
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} ${response.statusText}: ${typeof body === "string" ? body : JSON.stringify(body)}`);
    }
    return body;
  }

  function getPatientPayload() {
    return {
      name: $("patientName").value.trim(),
      email: $("patientEmail").value.trim(),
      address: $("patientAddress").value.trim(),
      dateOfBirth: $("patientDob").value,
      registeredDate: $("patientRegisteredDate").value,
    };
  }

  function getAiPayload() {
    const ageValue = $("aiAge").value.trim();
    const dob = $("aiDob").value;
    const payload = {
      patientId: $("aiPatientId").value.trim(),
      name: $("aiName").value.trim(),
      email: $("aiEmail").value.trim(),
      address: $("aiAddress").value.trim(),
      medicalHistory: $("aiMedicalHistory").value.trim(),
      currentMedications: $("aiCurrentMedications").value.trim(),
      allergies: $("aiAllergies").value.trim(),
    };
    if (dob) payload.dateOfBirth = dob;
    if (ageValue) payload.age = Number(ageValue);
    return payload;
  }

  async function execute(name, fn, renderer) {
    try {
      state.baseUrl = elements.baseUrl.value.trim();
      state.token = elements.jwtToken.value.trim();
      persistState();
      renderStatus(`${name} running`, "neutral");
      const result = await fn();
      renderResponse(result);
      if (renderer) renderer(result);
      renderStatus(`${name} success`, "success");
      log(`${name} success`);
      return result;
    } catch (error) {
      renderResponse({ error: error.message });
      renderMeaning(`<p><strong>${name} failed:</strong> ${error.message}</p>`);
      renderStatus(`${name} failed`, "error");
      log(`${name} failed: ${error.message}`);
      return null;
    }
  }

  function syncAiFromPatient(patient) {
    if (!patient) return;
    if (patient.id) {
      $("patientIdTarget").value = patient.id;
      $("aiPatientId").value = patient.id;
      $("predictionsPatientId").value = patient.id;
    }
    if (patient.name) $("aiName").value = patient.name;
    if (patient.email) $("aiEmail").value = patient.email;
    if (patient.address) $("aiAddress").value = patient.address;
    if (patient.dateOfBirth) $("aiDob").value = patient.dateOfBirth;
    const key = profileKey(patient);
    const profile = (key && state.patientProfiles[key]) || buildSyntheticProfile(patient);
    if (key && !state.patientProfiles[key]) {
      state.patientProfiles[key] = profile;
      persistState();
    }
    if (profile.age) $("aiAge").value = String(profile.age);
    if (profile.medicalHistory) $("aiMedicalHistory").value = profile.medicalHistory;
    if (profile.currentMedications) $("aiCurrentMedications").value = profile.currentMedications;
    if (profile.allergies) $("aiAllergies").value = profile.allergies;
    updateChatContext();
  }

  function updateChatContext() {
    if (!elements.chatContext) return;
    const patientId = $("aiPatientId")?.value?.trim() || "not set";
    const patientName = $("aiName")?.value?.trim() || "unknown";
    elements.chatContext.textContent = `Working patient: ${patientName} (${patientId})`;
    if (elements.activePatientBadge) {
      const selected = patientId !== "not set" && patientName !== "unknown";
      elements.activePatientBadge.textContent = selected
        ? `Active patient: ${patientName} (${patientId})`
        : "Active patient: not selected";
    }
  }

  async function login() {
    clearAuthFeedback();
    const email = elements.loginEmail.value.trim();
    const password = elements.loginPassword.value;
    if (!isValidEmail(email)) {
      showAuthFeedback("Enter a valid email address.", "login");
      renderStatus("Login failed", "error");
      return;
    }
    const passwordError = validatePassword(password);
    if (passwordError) {
      showAuthFeedback(passwordError, "login");
      renderStatus("Login failed", "error");
      return;
    }
    await execute("Login", async () => {
      const payload = { email: elements.loginEmail.value.trim(), password: elements.loginPassword.value };
      const result = await request("/auth/login", { method: "POST", body: JSON.stringify(payload) }, false);
      elements.jwtToken.value = result.token;
      state.token = result.token;
      persistState();
      syncAuthGate();
      updateSessionUserLabel();
      renderMeaning("<p>Logged in successfully. You now have full access to all portal pages.</p>");
      return result;
    });
    if (state.token) {
      clearAuthFeedback();
    } else {
      showAuthFeedback(mapAuthError(state.latestPayload?.error), "login");
    }
  }

  async function register() {
    clearAuthFeedback();
    const email = elements.registerEmail.value.trim();
    const password = elements.registerPassword.value;
    if (!isValidEmail(email)) {
      showAuthFeedback("Enter a valid email address.", "register");
      renderStatus("Register failed", "error");
      return;
    }
    const passwordError = validatePassword(password);
    if (passwordError) {
      showAuthFeedback(passwordError, "register");
      renderStatus("Register failed", "error");
      return;
    }
    await execute("Register", async () => {
      const payload = { email: elements.registerEmail.value.trim(), password: elements.registerPassword.value };
      if (!payload.email || !payload.password) throw new Error("Email and password are required.");
      await request("/auth/register", { method: "POST", body: JSON.stringify(payload) }, false);
      renderMeaning("<p>Account created successfully. Please sign in using the new credentials.</p>");
      return { message: "User created." };
    });
    if (state.latestPayload?.error) {
      showAuthFeedback(mapAuthError(state.latestPayload.error), "register");
    } else if (state.latestPayload) {
      const createdEmail = elements.registerEmail.value.trim();
      elements.loginEmail.value = createdEmail;
      elements.registerPassword.value = "";
      elements.showLoginBtn.click();
      showAuthFeedback(
        "Account created. Sign in with this email and the password you chose.",
        "login",
        true
      );
      log("Register success");
    }
  }

  function logout() {
    state.token = "";
    elements.jwtToken.value = "";
    persistState();
    syncAuthGate();
    updateSessionUserLabel();
    renderResponse({ message: "Logged out" });
    renderMeaning("<p>Session ended. Sign in to continue.</p>");
    renderStatus("Logged out", "neutral");
    log("Logged out");
  }

  async function createPatient() {
    await execute("Create Patient", async () => request("/api/patients", { method: "POST", body: JSON.stringify(getPatientPayload()) }, true), (result) => {
      syncAiFromPatient(result);
      renderMeaning(`<p>Patient created with ID <strong>${result.id || "N/A"}</strong>. Context synced to AI pages.</p>`);
    });
  }

  async function listPatients() {
    await execute("List Patients", async () => request("/api/patients", { method: "GET" }, true), (result) => {
      if (Array.isArray(result) && result.length > 0) syncAiFromPatient(result[0]);
      renderPatientsTable(result);
      renderMeaning(`<p>Loaded <strong>${Array.isArray(result) ? result.length : 0}</strong> patients.</p>`);
    });
  }

  function slug(value) {
    return (value || "").toLowerCase().replace(/[^a-z0-9]+/g, ".").replace(/^\.|\.$/g, "");
  }

  function profileKey(patient) {
    return String(patient?.id || "").trim();
  }

  function hashString(value) {
    const text = String(value || "");
    let hash = 0;
    for (let i = 0; i < text.length; i++) {
      hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0;
    }
    return Math.abs(hash);
  }

  function normalizeCondition(raw) {
    const condition = String(raw || "").trim().toLowerCase();
    if (!condition) return "hypertension";
    if (condition.includes("diab")) return "diabetes";
    if (condition.includes("asth")) return "asthma";
    if (condition.includes("arth")) return "arthritis";
    if (condition.includes("obes")) return "obesity";
    if (condition.includes("canc")) return "cancer";
    if (condition.includes("hyper")) return "hypertension";
    return condition;
  }

  function buildClinicalProfile(condition, age, gender, hospital) {
    const normalized = normalizeCondition(condition);
    const templates = {
      hypertension: {
        history: ["primary hypertension", "episodic elevated BP", "family cardiovascular risk"],
        medications: ["lisinopril 10 mg daily", "amlodipine 5 mg daily"],
        allergies: ["sulfa drugs", "ACE inhibitor cough history", "latex sensitivity"],
      },
      diabetes: {
        history: ["type 2 diabetes mellitus", "variable fasting glucose", "metabolic syndrome traits"],
        medications: ["metformin 500 mg twice daily", "glipizide 5 mg daily"],
        allergies: ["penicillin", "adhesive tape sensitivity", "none"],
      },
      asthma: {
        history: ["persistent asthma", "seasonal wheeze exacerbations", "allergic rhinitis history"],
        medications: ["albuterol inhaler as needed", "fluticasone inhaler daily"],
        allergies: ["dust mites", "pollen", "none"],
      },
      arthritis: {
        history: ["chronic osteoarthritis pain", "morning stiffness in large joints", "activity-limiting flare episodes"],
        medications: ["naproxen 250 mg as needed", "acetaminophen 650 mg as needed"],
        allergies: ["ibuprofen sensitivity", "naproxen intolerance", "none"],
      },
      obesity: {
        history: ["class II obesity", "sedentary lifestyle risk", "insulin resistance indicators"],
        medications: ["semaglutide weekly", "vitamin D supplementation"],
        allergies: ["shellfish", "none"],
      },
      cancer: {
        history: ["active oncology follow-up", "fatigue during treatment cycle", "monitoring for treatment complications"],
        medications: ["ondansetron as needed", "supportive corticosteroid taper"],
        allergies: ["contrast dye", "none"],
      },
    };
    const base = templates[normalized] || templates.hypertension;
    const ageGroup = Number(age || 45) >= 65 ? "older-adult profile" : "adult profile";
    const genderNote = gender ? `${String(gender).toLowerCase()} patient profile` : "general patient profile";
    const profileSeed = hashString(`${normalized}|${age}|${gender}|${hospital}`);
    const baseAllergies = base.allergies.filter((item) => item !== "none");
    const chooseNone = base.allergies.includes("none") && (profileSeed % 5 === 0);
    const selectedAllergies = chooseNone
      ? "No known drug allergies (NKDA)"
      : (profileSeed % 2 === 0
        ? `${baseAllergies[0]}`
        : `${baseAllergies[0]}, ${baseAllergies[1] || baseAllergies[0]}`);
    const history = `${base.history.join(", ")}; ${ageGroup}; ${genderNote}; care setting: ${hospital || "regional clinic"}.`;
    return {
      condition: normalized,
      age: Number(age || 45),
      gender: gender || "Unknown",
      hospital: hospital || "Regional Medical Center",
      medicalHistory: history,
      currentMedications: `${base.medications[0]}, ${base.medications[1]}`,
      allergies: selectedAllergies,
    };
  }

  function buildSyntheticProfile(patient) {
    const seed = hashString(`${patient?.id || ""}|${patient?.name || ""}|${patient?.email || ""}`);
    const conditions = ["hypertension", "diabetes", "asthma", "arthritis", "obesity"];
    const genders = ["Male", "Female"];
    const condition = conditions[seed % conditions.length];
    const age = 28 + (seed % 45);
    const gender = genders[seed % genders.length];
    return buildClinicalProfile(condition, age, gender, patient?.address || "City Health Network");
  }

  function dobFromAge(age) {
    const now = new Date();
    const year = Math.max(1930, now.getFullYear() - Number(age || 30));
    return `${year}-01-01`;
  }

  async function importDatasetPatients() {
    await execute("Import Dataset Patients", async () => {
      const count = Math.min(500, Math.max(1, Number($("importPatientCount").value || 10)));
      const dataset = await fetch("/data/healthcare_patients_seed.json").then((res) => res.json());
      const existing = await request("/api/patients", { method: "GET" }, true);
      const existingEmails = new Set((existing || []).map((p) => (p.email || "").toLowerCase()));
      const selected = [];
      for (let i = 0; i < count; i++) {
        selected.push(dataset[i % dataset.length]);
      }
      const created = [];
      let skipped = 0;
      const sequenceByBase = {};
      for (let i = 0; i < selected.length; i++) {
        const row = selected[i];
        const base = slug(row.name || "patient");
        sequenceByBase[base] = (sequenceByBase[base] || 0) + 1;
        let seq = sequenceByBase[base];
        let email = `${base}.${seq}@seed.local`;
        while (existingEmails.has(email.toLowerCase())) {
          seq += 1;
          email = `${base}.${seq}@seed.local`;
        }
        const payload = {
          name: row.name,
          email,
          address: `${row.hospital}, Indianapolis, IN`,
          dateOfBirth: dobFromAge(row.age),
          registeredDate: new Date().toISOString().slice(0, 10),
        };
        try {
          const result = await request("/api/patients", { method: "POST", body: JSON.stringify(payload) }, true);
          created.push(result);
          const key = profileKey(result);
          if (key) {
            state.patientProfiles[key] = buildClinicalProfile(
              row.condition,
              row.age,
              row.gender,
              row.hospital
            );
          }
          existingEmails.add(email.toLowerCase());
        } catch (error) {
          if ((error.message || "").includes("already exists")) {
            skipped += 1;
            continue;
          }
          throw error;
        }
      }
      persistState();
      return { imported: created.length, skipped, requested: count, sample: created.slice(0, 3) };
    }, (result) => {
      renderMeaning(
        `<p>Import completed: requested <strong>${result.requested}</strong>, imported <strong>${result.imported}</strong>, ` +
        `skipped duplicates <strong>${result.skipped || 0}</strong>.</p>`
      );
      listPatients();
    });
  }

  async function updatePatient() {
    await execute("Update Patient", async () => {
      const id = $("patientIdTarget").value.trim();
      if (!id) throw new Error("Patient ID is required.");
      return request(`/api/patients/${id}`, { method: "PUT", body: JSON.stringify(getPatientPayload()) }, true);
    }, (result) => {
      syncAiFromPatient(result);
      renderMeaning("<p>Patient record updated successfully.</p>");
    });
  }

  async function deletePatient() {
    await execute("Delete Patient", async () => {
      const id = $("patientIdTarget").value.trim();
      if (!id) throw new Error("Patient ID is required.");
      await request(`/api/patients/${id}`, { method: "DELETE" }, true);
      if (state.patientProfiles[id]) {
        delete state.patientProfiles[id];
        persistState();
      }
      return { message: "Delete successful" };
    }, () => {
      renderMeaning("<p>Patient record deleted successfully.</p>");
    });
  }

  async function deleteSelectedPatients() {
    await execute("Delete Selected Patients", async () => {
      const ids = [...state.selectedPatientIds].filter(Boolean);
      if (ids.length === 0) throw new Error("Select at least one patient from the list.");
      let deleted = 0;
      const failed = [];
      for (const id of ids) {
        try {
          await request(`/api/patients/${id}`, { method: "DELETE" }, true);
          if (state.patientProfiles[id]) {
            delete state.patientProfiles[id];
          }
          deleted += 1;
        } catch (error) {
          failed.push(id);
        }
      }
      persistState();
      state.selectedPatientIds.clear();
      return { deleted, failedCount: failed.length, failedIds: failed.slice(0, 10) };
    }, (result) => {
      renderMeaning(
        `<p>Bulk delete complete: removed <strong>${result.deleted}</strong> records.` +
        (result.failedCount ? ` Failed: <strong>${result.failedCount}</strong>.` : "") +
        `</p>`
      );
      listPatients();
    });
  }

  async function getAnalytics() {
    await execute("Get Analytics", async () => request("/api/analytics/statistics", { method: "GET" }, true), (result) => {
      const cards = Object.keys(result || {}).map((key) => ({ label: key, value: String(result[key]) }));
      renderCards(elements.analyticsCards, cards);
      const numeric = Object.entries(result || {})
        .filter((entry) => typeof entry[1] === "number")
        .slice(0, 8)
        .map((entry) => ({ label: entry[0], value: entry[1] }));
      renderBarChart(elements.analyticsChart, "Operational Metric Distribution", numeric, "");
      renderMeaning("<p>Analytics loaded. Review KPI cards for service trends.</p>");
    });
  }

  async function assessRisk() {
    await execute("Assess Risk", async () => request("/api/ai/assess-risk", { method: "POST", body: JSON.stringify(getAiPayload()) }, true), (result) => {
      renderCards(elements.clinicalInsights, [
        { label: "Risk Level", value: result.riskLevel || "N/A" },
        { label: "Risk Score", value: String(result.riskScore ?? "N/A") },
        { label: "Confidence", value: String(result.confidenceScore ?? "N/A") },
        { label: "Confidence Reason", value: result.confidenceReason || "Not provided" },
      ]);
      const factorEntries = Object.entries(result.factorContributions || {});
      const factorChart = factorEntries.map((entry) => ({
        label: entry[0],
        value: Number(entry[1]) || 0,
      }));
      renderBarChart(elements.riskFactorsChart, "Causal Factor Contribution Weights", factorChart, "");
      const contributionRows = factorEntries.map((entry) => ({
        factor: entry[0],
        contribution: Number(entry[1]).toFixed(2),
      }));
      renderTable(elements.riskContributionTable, contributionRows, [
        { key: "factor", label: "Factor" },
        { key: "contribution", label: "Contribution" },
      ]);
      renderMeaning(
        `<p><strong>Clinical meaning:</strong> patient risk is <strong>${result.riskLevel}</strong> with score <strong>${Number(result.riskScore || 0).toFixed(2)}</strong>. ` +
        `Top drivers: ${(result.riskFactors || []).join(", ") || "none listed"}. ` +
        `Confidence note: ${result.confidenceReason || "not provided"}.</p>`
      );
      state.findings.f1.runs += 1;
      state.findings.f1.examples.push(
        `${$("aiName").value || "Patient"}: ${result.riskLevel || "N/A"} (${Number(result.riskScore || 0).toFixed(2)})`
      );
      state.findings.f1.examples = state.findings.f1.examples.slice(-6);
      renderFindings();
    });
  }

  async function detectAnomalies() {
    await execute("Detect Anomalies", async () => request("/api/ai/detect-anomalies", { method: "POST", body: JSON.stringify(getAiPayload()) }, true), (result) => {
      renderMeaning(`<p><strong>Anomaly interpretation:</strong> ${result.assessment || "No details returned"}.</p>`);
    });
  }

  async function analyzeIntervention() {
    await execute("Analyze Intervention", async () => {
      const payload = {
        patient: getAiPayload(),
        interventionType: $("interventionType").value,
        interventionValue: $("interventionValue").value.trim(),
      };
      return request("/api/ai/analyze-intervention", { method: "POST", body: JSON.stringify(payload) }, true);
    }, (result) => {
      renderCards(elements.clinicalInsights, [
        { label: "Baseline Risk", value: String(result.baselineRiskScore) },
        { label: "Counterfactual Risk", value: String(result.counterfactualRiskScore) },
        { label: "Relative Reduction %", value: `${Number(result.relativeRiskReductionPct || 0).toFixed(2)}%` },
      ]);
      const comparison = [
        { label: "Baseline", value: Number(result.baselineRiskScore) || 0 },
        { label: "Counterfactual", value: Number(result.counterfactualRiskScore) || 0 },
      ];
      renderBarChart(elements.riskFactorsChart, "Risk Before vs After Intervention", comparison, "");
      renderMeaning(`<p><strong>Intervention meaning:</strong> ${result.recommendation || "No recommendation"} Delta risk: <strong>${result.deltaRiskScore}</strong>.</p>`);
      state.findings.f2.runs += 1;
      state.findings.f2.deltas.push(Number(result.deltaRiskScore) || 0);
      state.findings.f2.deltas = state.findings.f2.deltas.slice(-20);
      renderFindings();
    });
  }

  async function getPredictions() {
    await execute("Get Predictions", async () => {
      const patientId = $("predictionsPatientId").value.trim();
      if (!patientId) throw new Error("Predictions patient ID is required.");
      const predictionType = $("predictionType").value.trim();
      const suffix = predictionType ? `?predictionType=${encodeURIComponent(predictionType)}` : "";
      return request(`/api/ai/predictions/${encodeURIComponent(patientId)}${suffix}`, { method: "GET" }, true);
    }, (result) => {
      renderTable(elements.predictionsTable, result, [
        { key: "predictionType", label: "Type" },
        { key: "confidenceScore", label: "Confidence" },
        { key: "createdAt", label: "Created At" },
      ]);
      renderMeaning(`<p>Loaded <strong>${Array.isArray(result) ? result.length : 0}</strong> prediction records.</p>`);
    });
  }

  async function evaluateGuardrailDecision() {
    await execute("Guardrail Decision", async () => {
      const payload = captureCurrentChaosInputs();
      state.lastChaosInputs = payload;
      return request("/api/ai/causal-guardrail/decision", { method: "POST", body: JSON.stringify(payload) }, true);
    }, (result) => {
      renderCards(elements.guardrailCards, [
        { label: "Suspected Cause", value: result.suspectedCause || "N/A" },
        { label: "Selected Strategy", value: result.selectedStrategy || "N/A" },
        { label: "Model Mode", value: result.selectedModelMode || "N/A" },
        { label: "Confidence", value: String(result.confidenceScore || "N/A") },
      ]);
      renderMeaning(`<p><strong>Guardrail decision:</strong> ${result.rationale || "No rationale"}.</p>`);
      state.latestGuardrail = result;
      state.findings.f3.runs += 1;
      state.findings.f3.strategies.push(result.selectedStrategy || "N/A");
      state.findings.f3.strategies = state.findings.f3.strategies.slice(-20);
      renderFindings();
    });
  }

  async function getModelComparison() {
    await execute("Model Comparison", async () => request("/api/ai/model-comparison", { method: "GET" }, true), (result) => {
      const cards = (result || []).map((row) => ({
        label: row.modelName || "Model",
        value: `Latency ${row.avgLatencyMs}ms | Avail ${row.availabilityPct}% | Stability ${row.stabilityUnderFaultPct}%`,
      }));
      renderCards(elements.experimentCards, cards);
      renderBarChart(
        elements.comparisonChart,
        "Model Stability Under Fault",
        (result || []).map((row) => ({
          label: row.modelName || "Model",
          value: Number(row.stabilityUnderFaultPct) || 0,
        })),
        "%"
      );
      renderMeaning("<p>Model comparison loaded. Causal-Hybrid should show best balance of reliability and explainability.</p>");
    });
  }

  async function logExperimentRun() {
    await execute("Log Experiment", async () => {
      const protocolId = (($("protocolId")?.value || state.protocolId || "").trim());
      const protocolLabel = (($("protocolLabel")?.value || state.protocolLabel || "").trim());
      const payload = {
        modelName: $("expModelName").value,
        scenarioName: protocolId
          ? `[${protocolId}] ${$("expScenarioName").value.trim()}`
          : $("expScenarioName").value.trim(),
        avgLatencyMs: Number($("expLatencyMs").value || 0),
        availabilityPct: Number($("expAvailabilityPct").value || 0),
        stabilityUnderFaultPct: Number($("expStabilityPct").value || 0),
        explainabilityScore: Number($("expExplainabilityScore").value || 0),
        fallbackCorrectnessPct: Number($("expFallbackCorrectnessPct").value || 0),
        notes: `${$("expNotes").value.trim()}${protocolLabel ? ` | ProtocolLabel:${protocolLabel}` : ""}${protocolId ? ` | ProtocolId:${protocolId}` : ""}`,
      };
      return request("/api/ai/experiments/log", { method: "POST", body: JSON.stringify(payload) }, true);
    }, () => {
      renderMeaning("<p>Experiment run logged. Open Experiment Summary for aggregated evidence.</p>");
    });
  }

  async function getExperimentSummary() {
    await execute("Experiment Summary", async () => request("/api/ai/experiments/summary", { method: "GET" }, true), (result) => {
      const rows = (result.aggregatedByModel || []).map((row) => ({
        label: row.modelName,
        value: `Avg latency ${row.avgLatencyMs}ms, availability ${row.availabilityPct}%`,
      }));
      renderCards(elements.experimentCards, rows);
      renderBarChart(
        elements.experimentTrendChart,
        "Average Availability by Model",
        (result.aggregatedByModel || []).map((row) => ({
          label: row.modelName || "Model",
          value: Number(row.availabilityPct) || 0,
        })),
        "%"
      );
      renderMeaning(`<p>Experiment summary loaded. Total runs: <strong>${result.totalRuns || 0}</strong>.</p>`);
      state.findings.f4.totalRuns = Number(result.totalRuns || 0);
      renderFindings();
    });
  }

  async function runScenarioSuite() {
    await execute("Run Scenario Suite", async () =>
      request("/api/ai/experiments/run-scenarios", { method: "POST" }, true),
    (result) => {
      const strategies = Object.entries(result.guardrailStrategiesByScenario || {})
        .map((entry) => `${entry[0]} → ${entry[1]}`)
        .join("; ");
      renderMeaning(
        `<p>Scenario suite executed <strong>${result.executedRuns || 0}</strong> runs. ` +
        `Strategies: ${strategies || "none returned"}.</p>`
      );
      state.latestScenarioSuite = result;
      state.findings.f4.suites += 1;
      renderFindings();
    });
  }

  function stripLeadingStarQuotes(s) {
    let t = s;
    for (let k = 0; k < 10 && t.length; k++) {
      const stars = t.match(/^\s*([*•]+)\s*/);
      if (stars) {
        t = t.slice(stars[0].length);
        continue;
      }
      const q = t.match(/^\s*["\u201c]\s*/);
      if (q) {
        t = t.slice(q[0].length);
        continue;
      }
      break;
    }
    return t;
  }

  function sanitizeChatDisplay(text) {
    let s = String(text || "")
      .replace(/\u2014/g, "-")
      .replace(/\u2013/g, "-");
    s = stripLeadingStarQuotes(s);
    s = s.replace(/\n\nNext:\s*[^\n]+/g, "");
    if (/["\u201d]\s*$/.test(s) && !s.includes("\n")) {
      s = s.replace(/["\u201d]\s*$/, "").trim();
    }
    return s.trim();
  }

  function pushChat(role, message) {
    const bubble = document.createElement("div");
    bubble.className = `chat-bubble ${role}`;
    bubble.textContent = sanitizeChatDisplay(message);
    elements.chatThread.appendChild(bubble);
    elements.chatThread.scrollTop = elements.chatThread.scrollHeight;
  }

  async function flushPaint() {
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }

  function showThinkingBar() {
    const bar = elements.chatThinkingBar;
    if (!bar) return;
    bar.innerHTML =
      '<span class="thinking-ring" aria-hidden="true"></span>' +
      '<span class="thinking-label">Generating reply</span>' +
      '<span class="thinking-dots" aria-hidden="true"><span></span><span></span><span></span></span>';
    bar.hidden = false;
  }

  function hideThinkingBar() {
    const bar = elements.chatThinkingBar;
    if (!bar) return;
    bar.hidden = true;
    bar.innerHTML = "";
  }

  let chatAssistantPending = false;

  async function handleAssistantChat() {
    if (chatAssistantPending) return;
    const prompt = elements.chatInput.value.trim();
    if (!prompt) return;
    chatAssistantPending = true;
    elements.chatInput.value = "";
    const sendBtn = $("chatSendBtn");
    if (sendBtn) sendBtn.disabled = true;
    if (elements.chatInput) elements.chatInput.disabled = true;
    pushChat("user", prompt);
    showThinkingBar();
    await flushPaint();
    const thinkingStarted = Date.now();
    const minThinkingMs = 520;
    state.findings.f5.prompts += 1;
    try {
      state.baseUrl = elements.baseUrl.value.trim();
      state.token = elements.jwtToken.value.trim();
      persistState();
      const result = await request(
        "/api/ai/chat",
        {
          method: "POST",
          body: JSON.stringify({
            message: prompt,
            patientContext: getAiPayload(),
          }),
        },
        true
      );
      const waitMore = minThinkingMs - (Date.now() - thinkingStarted);
      if (waitMore > 0) await new Promise((r) => setTimeout(r, waitMore));
      hideThinkingBar();
      pushChat("assistant", result.answer || "No answer returned.");
      renderFindings();
    } catch (error) {
      const waitMore = minThinkingMs - (Date.now() - thinkingStarted);
      if (waitMore > 0) await new Promise((r) => setTimeout(r, waitMore));
      hideThinkingBar();
      state.findings.f5.failures += 1;
      pushChat(
        "assistant",
        "I could not answer right now. Please try again in a few seconds."
      );
      renderFindings();
    } finally {
      chatAssistantPending = false;
      if (sendBtn) sendBtn.disabled = false;
      if (elements.chatInput) elements.chatInput.disabled = false;
    }
  }

  init();
})();
