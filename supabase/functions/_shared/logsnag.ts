/** Closed operational notifications. No account IDs, receipt tokens, emails or Aquarium content. */
export function paymentNotification(value: Record<string, unknown>, project: string): Record<string, unknown> | null {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(project)) throw new Error("Invalid LogSnag project");
  if (value.store !== "PLAY_STORE" || !["PRODUCTION", "SANDBOX"].includes(String(value.environment))) return null;
  const product = typeof value.product_id === "string" ? value.product_id.split(":")[0] : "";
  const plan = product === "aqualog_pro_annual" ? "annual" : product === "aqualog_pro_lifetime" ? "lifetime" : null;
  if (!plan) return null;
  const paid = typeof value.price === "number" && value.price > 0;
  const labels: Record<string, string> = {
    INITIAL_PURCHASE: value.period_type === "TRIAL" ? "Essai démarré" : paid ? "Paiement confirmé" : "Abonnement activé",
    RENEWAL: paid ? "Paiement confirmé" : "Droit renouvelé",
    NON_RENEWING_PURCHASE: "Achat à vie confirmé",
    CANCELLATION: "Annulation signalée",
    EXPIRATION: "Droit Pro expiré",
    BILLING_ISSUE: "Incident de paiement",
  };
  const event = labels[String(value.type)];
  if (!event) return null;
  const sandbox = value.environment === "SANDBOX";
  return { project, channel: sandbox ? "payments-test" : "payments", event, icon: "💳", notify: !sandbox,
    tags: { plan, environment: sandbox ? "sandbox" : "production" } };
}

export type DeliveryDependencies = {
  claim(hash: string): Promise<"claimed" | "sent" | "busy">;
  complete(hash: string, delivered: boolean): Promise<void>;
  send(payload: Record<string, unknown>): Promise<void>;
};
export async function deliverPaymentNotification(
  event: Record<string, unknown>, project: string, dependencies: DeliveryDependencies,
): Promise<void> {
  const payload = paymentNotification(event, project);
  if (!payload) return;
  if (typeof event.id !== "string" || !event.id || event.id.length > 200) throw new Error("Missing event identity");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(event.id));
  const hash = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  const claim = await dependencies.claim(hash);
  if (claim === "sent") return;
  if (claim === "busy") throw new Error("Notification delivery is in progress");
  try {
    await dependencies.send(payload);
    await dependencies.complete(hash, true);
  } catch (_error) {
    await dependencies.complete(hash, false);
    throw new Error("Notification delivery needs retry");
  }
}
