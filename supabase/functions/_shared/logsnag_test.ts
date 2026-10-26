import { deliverPaymentNotification, paymentNotification } from "./logsnag.ts";
function assert(value: unknown): asserts value { if (!value) throw new Error("assertion failed"); }
const purchase = { id: "event-1", store: "PLAY_STORE", environment: "PRODUCTION", product_id: "aqualog_pro_annual:annual", type: "INITIAL_PURCHASE", period_type: "NORMAL", price: 14.99 };
Deno.test("payment notifications exclude customer content and distinguish trials from payments", () => {
  const payload = paymentNotification({ ...purchase, app_user_id: "private-account", email: "private@example.com", notes: "private-content" }, "aqualog")!;
  assert(payload.event === "Paiement confirmé" && payload.notify === true && payload.channel === "payments");
  assert(!JSON.stringify(payload).includes("private"));
  assert(paymentNotification({ ...purchase, period_type: "TRIAL", price: 0 }, "aqualog")!.event === "Essai démarré");
  assert(paymentNotification({ ...purchase, environment: "SANDBOX" }, "aqualog")!.notify === false);
  assert(paymentNotification({ ...purchase, environment: "SANDBOX" }, "aqualog")!.channel === "payments-test");
  for (const changed of [{ store: "APP_STORE" }, { environment: "unknown" }, { product_id: "other" }, { type: "unknown" }]) {
    assert(paymentNotification({ ...purchase, ...changed }, "aqualog") === null);
  }
});
Deno.test("successful payment notifications deduplicate repeated webhooks with opaque receipts", async () => {
  const sent = new Set<string>(); let count = 0;
  const dependencies = {
    claim: async (hash: string): Promise<"claimed" | "sent"> => { assert(/^[a-f0-9]{64}$/.test(hash)); return sent.has(hash) ? "sent" : "claimed"; },
    complete: async (hash: string, delivered: boolean) => { if (delivered) sent.add(hash); },
    send: async () => { count++; },
  };
  await deliverPaymentNotification(purchase, "aqualog", dependencies);
  await deliverPaymentNotification(purchase, "aqualog", dependencies);
  assert(count === 1);
});
Deno.test("notification failure releases reservation and busy deliveries ask for retry", async () => {
  let released = false; let failed = false;
  try { await deliverPaymentNotification(purchase, "aqualog", {
    claim: async () => "claimed", complete: async (_, delivered) => { released = !delivered; }, send: async () => { throw new Error("offline"); },
  }); } catch { failed = true; }
  assert(failed && released);
  failed = false;
  try { await deliverPaymentNotification(purchase, "aqualog", {
    claim: async () => "busy", complete: async () => { throw new Error("unexpected"); }, send: async () => { throw new Error("unexpected"); },
  }); } catch { failed = true; }
  assert(failed);
});
