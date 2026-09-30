// Throwaway prototype: three rapid-session flows, switchable with ?variant=A|B|C.
const VARIANTS = {
  A: "A · Formulaire continu",
  B: "B · Assistant guidé",
  C: "C · Checklist compacte",
};

const MEASURES = [
  { id: "temperature", label: "Température", unit: "°C", previous: "24,3", target: "23–26" },
  { id: "nitrate", label: "Nitrates", unit: "mg/L", previous: "10", target: "< 20" },
  { id: "ph", label: "pH", unit: "", previous: "7,1", target: "6,8–7,5" },
];

const SCENARIOS = {
  routine: {
    label: "Routine",
    tasks: [
      ["temperature", "Saisir température : 24,5 °C"],
      ["nitrate", "Saisir nitrates : 12 mg/L"],
      ["ph", "Saisir pH : 7,2"],
      ["waterChange", "Activer le changement d'eau : 25 %"],
    ],
  },
  complete: {
    label: "Session complète",
    tasks: [
      ["temperature", "Saisir température : 24,5 °C"],
      ["nitrate", "Saisir nitrates : 12 mg/L"],
      ["ph", "Saisir pH : 7,2"],
      ["waterChange", "Activer le changement d'eau : 25 %"],
      ["filter", "Activer l'entretien du filtre"],
      ["observation", "Noter : « Poissons actifs »"],
    ],
  },
};

const initialSession = () => ({
  aquarium: "Rio 180",
  occurredAt: "Aujourd'hui · maintenant",
  measurements: { temperature: "", nitrate: "", ph: "" },
  actions: {
    waterChange: { selected: false, percent: 25 },
    fertilization: { selected: false, product: "Engrais complet", amount: 5, unit: "ml" },
    filter: { selected: false },
    pruning: { selected: false },
  },
  observation: "",
  saved: false,
});

let variant = getVariant();
let scenario = new URLSearchParams(location.search).get("scenario") === "complete" ? "complete" : "routine";
let session = initialSession();
let ui = { step: 0, measureIndex: 0, observationOpen: false, sheetMeasure: null };
let metrics = freshMetrics();
let timerHandle = null;
const countedFields = new Set();

const app = document.querySelector("#app");
const stateOutput = document.querySelector("#state-output");

function freshMetrics() {
  return { startedAt: null, elapsedMs: 0, actions: 0, screens: 1, completedAt: null };
}

function getVariant() {
  const value = new URLSearchParams(location.search).get("variant")?.toUpperCase();
  return Object.hasOwn(VARIANTS, value) ? value : "A";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function startTimer() {
  if (metrics.startedAt || metrics.completedAt) return;
  metrics.startedAt = performance.now();
  timerHandle = window.setInterval(() => {
    metrics.elapsedMs = performance.now() - metrics.startedAt;
    renderMetrics();
  }, 100);
}

function trackAction({ screen = false, field } = {}) {
  startTimer();
  metrics.actions += 1;
  if (screen) metrics.screens += 1;
  if (field) countedFields.add(field);
  renderMetrics();
}

function resetPrototype({ keepVariant = true } = {}) {
  window.clearInterval(timerHandle);
  timerHandle = null;
  session = initialSession();
  ui = { step: 0, measureIndex: 0, observationOpen: false, sheetMeasure: null };
  metrics = freshMetrics();
  countedFields.clear();
  if (!keepVariant) variant = "A";
  render();
}

function cycleVariant(direction) {
  const keys = Object.keys(VARIANTS);
  const nextIndex = (keys.indexOf(variant) + direction + keys.length) % keys.length;
  variant = keys[nextIndex];
  const params = new URLSearchParams(location.search);
  params.set("variant", variant);
  params.set("scenario", scenario);
  history.replaceState(null, "", `?${params.toString()}`);
  resetPrototype();
}

function setScenario(next) {
  scenario = next;
  const params = new URLSearchParams(location.search);
  params.set("variant", variant);
  params.set("scenario", scenario);
  history.replaceState(null, "", `?${params.toString()}`);
  resetPrototype();
}

function taskDone(id) {
  if (id in session.measurements) {
    const expected = { temperature: 24.5, nitrate: 12, ph: 7.2 }[id];
    const actual = Number(session.measurements[id].replace(",", "."));
    return Math.abs(actual - expected) < 0.001;
  }
  if (id === "waterChange") return session.actions.waterChange.selected && session.actions.waterChange.percent === 25;
  if (id === "filter") return session.actions.filter.selected;
  if (id === "observation") return session.observation.trim().toLocaleLowerCase("fr") === "poissons actifs";
  return false;
}

function missionComplete() {
  return SCENARIOS[scenario].tasks.every(([id]) => taskDone(id));
}

function hasContent() {
  return Object.values(session.measurements).some(Boolean)
    || Object.values(session.actions).some((action) => action.selected)
    || session.observation.trim().length > 0;
}

function completedItemCount() {
  return SCENARIOS[scenario].tasks.filter(([id]) => taskDone(id)).length;
}

function isUnusual(id, rawValue) {
  if (!rawValue) return false;
  const value = Number(rawValue.replace(",", "."));
  if (!Number.isFinite(value)) return false;
  if (id === "temperature") return value < 23 || value > 26;
  if (id === "nitrate") return value > 20;
  if (id === "ph") return value < 6.8 || value > 7.5;
  return false;
}

function measureHint(measure, rawValue) {
  return isUnusual(measure.id, rawValue)
    ? "Valeur inhabituelle — vérifiez la saisie"
    : `Dernière : ${measure.previous}${measure.unit ? ` ${measure.unit}` : ""} · Repère ${measure.target}`;
}

function updateInlineWarning(container, id, rawValue) {
  const measure = MEASURES.find((item) => item.id === id);
  const unusual = isUnusual(id, rawValue);
  container?.classList.toggle("unusual", unusual);
  const hint = container?.querySelector("small");
  if (hint) {
    hint.textContent = measureHint(measure, rawValue);
    hint.classList.toggle("warning", unusual);
  }
}

function finishSession() {
  trackAction({ screen: true });
  session.saved = true;
  metrics.elapsedMs = metrics.startedAt ? performance.now() - metrics.startedAt : 0;
  metrics.completedAt = performance.now();
  window.clearInterval(timerHandle);
  render();
}

function render() {
  document.querySelector("#variant-label").textContent = VARIANTS[variant];
  document.querySelectorAll("[data-scenario]").forEach((button) => {
    button.classList.toggle("selected", button.dataset.scenario === scenario);
  });
  renderMission();

  if (session.saved) {
    app.innerHTML = savedScreen();
  } else if (variant === "A") {
    app.innerHTML = variantA();
  } else if (variant === "B") {
    app.innerHTML = variantB();
  } else {
    app.innerHTML = variantC();
  }

  bindAppEvents();
  renderMetrics();
}

function renderMission() {
  document.querySelector("#mission-list").innerHTML = SCENARIOS[scenario].tasks
    .map(([id, label]) => `<li class="${taskDone(id) ? "done" : ""}">${escapeHtml(label)}</li>`)
    .join("");
}

function renderMetrics() {
  const elapsed = metrics.completedAt
    ? metrics.elapsedMs
    : metrics.startedAt
      ? performance.now() - metrics.startedAt
      : 0;
  document.querySelector("#metric-time").textContent = `${(elapsed / 1000).toFixed(1).replace(".", ",")} s`;
  document.querySelector("#metric-actions").textContent = metrics.actions;
  document.querySelector("#metric-screens").textContent = metrics.screens;
  document.querySelector("#metric-fields").textContent = `${completedItemCount()}/${SCENARIOS[scenario].tasks.length}`;

  const chip = document.querySelector("#completion-chip");
  if (session.saved && missionComplete() && elapsed < 30_000) {
    chip.textContent = "Réussi < 30 s";
    chip.className = "status-chip success";
  } else if (session.saved) {
    chip.textContent = missionComplete() ? "Plus de 30 s" : "Informations manquantes";
    chip.className = "status-chip warning";
  } else {
    chip.textContent = "En attente";
    chip.className = "status-chip neutral";
  }

  stateOutput.textContent = JSON.stringify({
    variant,
    scenario,
    ui,
    session,
    metrics: {
      elapsedSeconds: Number((elapsed / 1000).toFixed(1)),
      meaningfulActions: metrics.actions,
      screensVisited: metrics.screens,
      completedMissionItems: completedItemCount(),
      missionComplete: missionComplete(),
    },
  }, null, 2);

  renderMission();
}

function appBar(title, closeLabel = "Fermer") {
  return `
    <header class="top-app-bar">
      <button class="icon-button" data-noop aria-label="${closeLabel}">✕</button>
      <h2>${title}</h2>
      <button class="icon-button" data-noop aria-label="Plus d'options">⋮</button>
    </header>`;
}

function contextRow() {
  return `
    <div class="context-row">
      <div><strong>Rio 180</strong><span>Aujourd'hui · maintenant</span></div>
      <button type="button" data-noop>Modifier</button>
    </div>`;
}

function measureFields() {
  return MEASURES.map((measure, index) => {
    const unusual = isUnusual(measure.id, session.measurements[measure.id]);
    return `
    <label class="measure-field ${unusual ? "unusual" : ""}">
      <span class="measure-copy">
        <strong>${measure.label}</strong>
        <small class="${unusual ? "warning" : ""}">${measureHint(measure, session.measurements[measure.id])}</small>
      </span>
      <span class="measure-input-wrap">
        <input
          inputmode="decimal"
          autocomplete="off"
          aria-label="${measure.label}"
          data-measure="${measure.id}"
          data-index="${index}"
          value="${escapeHtml(session.measurements[measure.id])}"
          placeholder="—"
          ${index === 0 ? "autofocus" : ""}
        />
        <span>${measure.unit}</span>
      </span>
    </label>`;
  }).join("");
}

function actionTile(id, title, detail) {
  const selected = session.actions[id].selected;
  return `
    <button type="button" class="action-tile ${selected ? "selected" : ""}" data-action="${id}" aria-pressed="${selected}">
      <span class="checkmark">${selected ? "✓" : ""}</span>
      <strong>${title}</strong>
      <span>${detail}</span>
    </button>`;
}

function quickActions() {
  return `
    <div class="quick-actions">
      ${actionTile("waterChange", "Changement d'eau", "Dernier : 25 %")}
      ${actionTile("fertilization", "Fertilisation", "Engrais complet · 5 ml")}
      ${actionTile("filter", "Entretien filtre", "Détails facultatifs")}
      ${actionTile("pruning", "Taille des plantes", "Détails facultatifs")}
    </div>`;
}

function variantA() {
  return `
    <div class="screen">
      ${appBar("Nouvelle session")}
      <div class="scroll-content">
        ${contextRow()}
        <h1 class="screen-title">Mesures du jour</h1>
        <p class="screen-subtitle">Un champ vide sera simplement ignoré.</p>
        <div class="field-list">${measureFields()}</div>
        <h3 class="section-label">Actions réalisées</h3>
        ${quickActions()}
        <button type="button" class="disclosure" data-toggle-observation>
          ${ui.observationOpen ? "−" : "+"} Observation facultative
        </button>
        ${ui.observationOpen ? `<textarea class="observation-box" data-observation placeholder="Ajouter une observation…">${escapeHtml(session.observation)}</textarea>` : ""}
      </div>
      <div class="bottom-action">
        <button type="button" class="primary-button" data-save ${hasContent() ? "" : "disabled"}>Enregistrer la session</button>
      </div>
    </div>`;
}

function progress() {
  return `<div class="step-progress" aria-label="Étape ${ui.step + 1} sur 4">${[0, 1, 2, 3].map((step) => `<span class="${step <= ui.step ? "active" : ""}"></span>`).join("")}</div>`;
}

function variantB() {
  const screens = [stepMeasurements, stepActions, stepObservation, stepReview];
  return `
    <div class="screen">
      ${appBar("Session guidée")}
      ${progress()}
      ${screens[ui.step]()}
    </div>`;
}

function stepMeasurements() {
  const measure = MEASURES[ui.measureIndex];
  const unusual = isUnusual(measure.id, session.measurements[measure.id]);
  return `
    <div class="scroll-content">
      <p class="eyebrow">Étape 1 · Mesures</p>
      <h1 class="screen-title">${measure.label}</h1>
      <p class="screen-subtitle">Mesure ${ui.measureIndex + 1} sur ${MEASURES.length}. Laissez vide pour ignorer.</p>
      <div class="big-measure ${unusual ? "unusual" : ""}">
        <div>
          <label for="guided-value">${measure.label}</label>
          <small>${measureHint(measure, session.measurements[measure.id])}</small>
          <div class="hero-input">
            <input id="guided-value" inputmode="decimal" autocomplete="off" data-guided-measure="${measure.id}" value="${escapeHtml(session.measurements[measure.id])}" autofocus />
            <span>${measure.unit}</span>
          </div>
        </div>
      </div>
      <div class="measure-dots">
        ${MEASURES.map((item, index) => `<button type="button" data-measure-index="${index}" class="${index === ui.measureIndex ? "current" : session.measurements[item.id] ? "done" : ""}" aria-label="${item.label}">${index + 1}</button>`).join("")}
      </div>
    </div>
    <div class="bottom-action step-actions">
      <button type="button" class="text-button" data-skip>Ignorer</button>
      <button type="button" class="primary-button" data-guided-next>${ui.measureIndex < MEASURES.length - 1 ? "Suivant" : "Continuer"}</button>
    </div>`;
}

function stepActions() {
  return `
    <div class="scroll-content">
      <p class="eyebrow">Étape 2 · Entretien</p>
      <h1 class="screen-title">Qu'avez-vous fait ?</h1>
      <p class="screen-subtitle">Touchez une action pour reprendre ses derniers détails.</p>
      ${quickActions()}
    </div>
    ${stepFooter("Mesures", "Continuer")}`;
}

function stepObservation() {
  return `
    <div class="scroll-content">
      <p class="eyebrow">Étape 3 · Facultatif</p>
      <h1 class="screen-title">Une observation ?</h1>
      <p class="screen-subtitle">Comportement, aspect de l'eau ou événement notable.</p>
      <textarea class="observation-box" data-observation placeholder="Ex. Poissons actifs">${escapeHtml(session.observation)}</textarea>
    </div>
    ${stepFooter("Retour", "Vérifier")}`;
}

function stepReview() {
  const measurements = MEASURES.filter((item) => session.measurements[item.id])
    .map((item) => `${item.label} ${escapeHtml(session.measurements[item.id])}${item.unit ? ` ${item.unit}` : ""}`).join(" · ") || "Aucune mesure";
  const actions = [
    session.actions.waterChange.selected && `Changement d'eau ${session.actions.waterChange.percent} %`,
    session.actions.fertilization.selected && "Fertilisation 5 ml",
    session.actions.filter.selected && "Entretien filtre",
    session.actions.pruning.selected && "Taille des plantes",
  ].filter(Boolean).join(" · ") || "Aucune action";
  return `
    <div class="scroll-content">
      <p class="eyebrow">Étape 4 · Validation</p>
      <h1 class="screen-title">Tout est correct ?</h1>
      <p class="screen-subtitle">La session sera enregistrée localement en une fois.</p>
      <div class="summary-list">
        <div class="summary-row"><span>Aquarium</span><strong>Rio 180</strong></div>
        <div class="summary-row"><span>Mesures</span><strong>${measurements}</strong></div>
        <div class="summary-row"><span>Entretien</span><strong>${actions}</strong></div>
        <div class="summary-row"><span>Observation</span><strong>${escapeHtml(session.observation) || "Aucune"}</strong></div>
      </div>
    </div>
    <div class="bottom-action step-actions">
      <button type="button" class="text-button" data-step-back>Modifier</button>
      <button type="button" class="primary-button" data-save ${hasContent() ? "" : "disabled"}>Enregistrer</button>
    </div>`;
}

function stepFooter(back, next) {
  return `
    <div class="bottom-action step-actions">
      <button type="button" class="text-button" data-step-back>${back}</button>
      <button type="button" class="primary-button" data-step-next>${next}</button>
    </div>`;
}

function checklistMeasureRow(measure) {
  const value = session.measurements[measure.id];
  const unusual = isUnusual(measure.id, value);
  return `
    <button type="button" class="check-row ${value ? "completed" : ""} ${unusual ? "unusual" : ""}" data-open-measure="${measure.id}">
      <span class="check-row-icon">✓</span>
      <span><strong>${measure.label}</strong><small>${unusual ? "Valeur inhabituelle — à vérifier" : `Dernière : ${measure.previous}${measure.unit ? ` ${measure.unit}` : ""}`}</small></span>
      <span class="check-row-value">${value ? `${escapeHtml(value)} ${measure.unit}` : "Saisir"}</span>
    </button>`;
}

function checklistActionRow(id, label, detail) {
  const selected = session.actions[id].selected;
  return `
    <button type="button" class="check-row ${selected ? "selected" : ""}" data-action="${id}">
      <span class="check-row-icon">✓</span>
      <span><strong>${label}</strong><small>${detail}</small></span>
      <span class="check-row-value">${selected ? "Fait" : "Ajouter"}</span>
    </button>`;
}

function variantC() {
  return `
    <div class="screen">
      ${appBar("Session express")}
      <div class="checklist-header">
        <h3>Routine de Rio 180</h3>
        <p>Aujourd'hui · paramètres et ordre de la dernière session</p>
      </div>
      <div class="checklist">
        <section class="check-section">
          <div class="check-section-title">Mesures</div>
          ${MEASURES.map(checklistMeasureRow).join("")}
        </section>
        <section class="check-section">
          <div class="check-section-title">Entretien</div>
          ${checklistActionRow("waterChange", "Changement d'eau", "Dernier détail : 25 %")}
          ${checklistActionRow("fertilization", "Fertilisation", "Engrais complet · 5 ml")}
          ${checklistActionRow("filter", "Entretien filtre", "Sans détail")}
          ${checklistActionRow("pruning", "Taille des plantes", "Sans détail")}
        </section>
        <section class="check-section">
          <div class="check-section-title">Facultatif</div>
          <button type="button" class="check-row ${session.observation ? "completed" : ""}" data-toggle-observation>
            <span class="check-row-icon">✓</span>
            <span><strong>Observation</strong><small>${session.observation ? escapeHtml(session.observation) : "Aucune note"}</small></span>
            <span class="check-row-value">${session.observation ? "Modifier" : "Ajouter"}</span>
          </button>
          ${ui.observationOpen ? `<textarea class="observation-box" data-observation placeholder="Ajouter une observation…">${escapeHtml(session.observation)}</textarea>` : ""}
        </section>
      </div>
      <div class="bottom-action">
        <button type="button" class="primary-button" data-save ${hasContent() ? "" : "disabled"}>Enregistrer la session</button>
      </div>
      ${ui.sheetMeasure ? measureSheet(ui.sheetMeasure) : ""}
    </div>`;
}

function measureSheet(id) {
  const measure = MEASURES.find((item) => item.id === id);
  return `
    <div class="sheet-scrim" data-close-sheet>
      <div class="bottom-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title">
        <div class="sheet-handle"></div>
        <h3 id="sheet-title">${measure.label}</h3>
        <p>Dernière : ${measure.previous}${measure.unit ? ` ${measure.unit}` : ""} · Repère ${measure.target}</p>
        <label class="sheet-input">
          <input data-sheet-input inputmode="decimal" autocomplete="off" value="${escapeHtml(session.measurements[id])}" autofocus />
          <span>${measure.unit}</span>
        </label>
        <div class="sheet-buttons">
          <button type="button" class="cancel" data-close-sheet>Ignorer</button>
          <button type="button" class="confirm" data-confirm-measure="${id}">Valider</button>
        </div>
      </div>
    </div>`;
}

function savedScreen() {
  const measurementCount = Object.values(session.measurements).filter(Boolean).length;
  const actionCount = Object.values(session.actions).filter((action) => action.selected).length;
  return `
    <div class="screen saved-screen">
      <div>
        <div class="saved-icon">✓</div>
        <h2>Session enregistrée</h2>
        <p>Disponible sur ce téléphone, même hors connexion.</p>
        <div class="timeline-card">
          <strong>Maintenant · Rio 180</strong>
          <span>${measurementCount} mesure${measurementCount > 1 ? "s" : ""} · ${actionCount} action${actionCount > 1 ? "s" : ""}${session.observation ? " · 1 observation" : ""}</span>
        </div>
        <button type="button" class="tonal-button" data-reset>Tester à nouveau</button>
      </div>
    </div>`;
}

function normalizeDecimal(value) {
  return value.replace(".", ",").replace(/[^0-9,]/g, "").replace(/(,.*),/g, "$1");
}

function bindAppEvents() {
  app.querySelectorAll("[data-noop]").forEach((button) => button.addEventListener("click", () => {}));

  app.querySelectorAll("[data-measure]").forEach((input) => {
    input.addEventListener("input", (event) => {
      const id = event.currentTarget.dataset.measure;
      session.measurements[id] = normalizeDecimal(event.currentTarget.value);
      event.currentTarget.value = session.measurements[id];
      updateInlineWarning(event.currentTarget.closest(".measure-field"), id, session.measurements[id]);
      if (!countedFields.has(id) && session.measurements[id]) trackAction({ field: id });
      else {
        startTimer();
        renderMetrics();
      }
      const saveButton = app.querySelector("[data-save]");
      if (saveButton) saveButton.disabled = !hasContent();
    });
    input.addEventListener("keydown", (event) => {
      if (event.key !== "Enter") return;
      event.preventDefault();
      event.currentTarget.dispatchEvent(new Event("change"));
      const next = app.querySelector(`[data-index="${Number(event.currentTarget.dataset.index) + 1}"]`);
      next?.focus();
    });
  });

  app.querySelectorAll("[data-action]").forEach((button) => button.addEventListener("click", () => {
    const id = button.dataset.action;
    session.actions[id].selected = !session.actions[id].selected;
    trackAction({ field: id });
    render();
  }));

  app.querySelectorAll("[data-toggle-observation]").forEach((button) => button.addEventListener("click", () => {
    ui.observationOpen = !ui.observationOpen;
    trackAction();
    render();
    if (ui.observationOpen) app.querySelector("[data-observation]")?.focus();
  }));

  const observation = app.querySelector("[data-observation]");
  if (observation) {
    observation.addEventListener("input", (event) => {
      session.observation = event.currentTarget.value;
      if (!countedFields.has("observation") && session.observation.trim()) trackAction({ field: "observation" });
      else {
        startTimer();
        renderMetrics();
      }
    });
  }

  app.querySelector("[data-save]")?.addEventListener("click", finishSession);
  app.querySelector("[data-reset]")?.addEventListener("click", () => resetPrototype());

  app.querySelectorAll("[data-measure-index]").forEach((button) => button.addEventListener("click", () => {
    ui.measureIndex = Number(button.dataset.measureIndex);
    trackAction({ screen: true });
    render();
  }));

  app.querySelector("[data-guided-measure]")?.addEventListener("input", (event) => {
    const id = event.currentTarget.dataset.guidedMeasure;
    session.measurements[id] = normalizeDecimal(event.currentTarget.value);
    event.currentTarget.value = session.measurements[id];
    updateInlineWarning(event.currentTarget.closest(".big-measure"), id, session.measurements[id]);
    if (!countedFields.has(id) && session.measurements[id]) trackAction({ field: id });
    else {
      startTimer();
      renderMetrics();
    }
  });

  app.querySelector("[data-guided-next]")?.addEventListener("click", () => {
    const measure = MEASURES[ui.measureIndex];
    if (session.measurements[measure.id]) countedFields.add(measure.id);
    if (ui.measureIndex < MEASURES.length - 1) {
      ui.measureIndex += 1;
    } else {
      ui.step = 1;
    }
    trackAction({ screen: true });
    render();
  });

  app.querySelector("[data-skip]")?.addEventListener("click", () => {
    if (ui.measureIndex < MEASURES.length - 1) ui.measureIndex += 1;
    else ui.step = 1;
    trackAction({ screen: true });
    render();
  });

  app.querySelector("[data-step-next]")?.addEventListener("click", () => {
    ui.step = Math.min(3, ui.step + 1);
    trackAction({ screen: true });
    render();
  });
  app.querySelector("[data-step-back]")?.addEventListener("click", () => {
    ui.step = Math.max(0, ui.step - 1);
    trackAction({ screen: true });
    render();
  });

  app.querySelectorAll("[data-open-measure]").forEach((button) => button.addEventListener("click", () => {
    ui.sheetMeasure = button.dataset.openMeasure;
    trackAction();
    render();
    requestAnimationFrame(() => app.querySelector("[data-sheet-input]")?.focus());
  }));

  app.querySelectorAll("[data-close-sheet]").forEach((element) => element.addEventListener("click", (event) => {
    if (event.target.closest(".bottom-sheet") && !event.target.matches("[data-close-sheet]")) return;
    event.stopPropagation();
    ui.sheetMeasure = null;
    trackAction();
    render();
  }));

  app.querySelector("[data-sheet-input]")?.addEventListener("input", () => {
    const id = ui.sheetMeasure;
    if (!countedFields.has(id)) trackAction({ field: id });
    else {
      startTimer();
      renderMetrics();
    }
  });

  app.querySelector("[data-confirm-measure]")?.addEventListener("click", (event) => {
    const id = event.currentTarget.dataset.confirmMeasure;
    session.measurements[id] = normalizeDecimal(app.querySelector("[data-sheet-input]").value);
    ui.sheetMeasure = null;
    trackAction({ field: id });
    render();
  });
}

document.querySelector("#variant-prev").addEventListener("click", () => cycleVariant(-1));
document.querySelector("#variant-next").addEventListener("click", () => cycleVariant(1));
document.querySelector("#reset-button").addEventListener("click", () => resetPrototype());
document.querySelectorAll("[data-scenario]").forEach((button) => button.addEventListener("click", () => setScenario(button.dataset.scenario)));

window.addEventListener("keydown", (event) => {
  const target = event.target;
  const editing = target.matches("input, textarea, [contenteditable='true']");
  if (editing || !["ArrowLeft", "ArrowRight"].includes(event.key)) return;
  event.preventDefault();
  cycleVariant(event.key === "ArrowRight" ? 1 : -1);
});

render();
