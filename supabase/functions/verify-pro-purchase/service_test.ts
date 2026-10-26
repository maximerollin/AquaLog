import { handleProRequest, handleRevenueCatWebhook, interpretRevenueCatCustomer, revenueCatAppUserId } from "./service.ts";

const now = Date.parse("2026-10-26T12:00:00Z");
const future = "2027-10-26T12:00:00Z";
const past = "2025-10-26T12:00:00Z";
function assert(value: unknown, message = "assertion failed"): void { if (!value) throw new Error(message); }
function customer(product = "aqualog_pro_annual", expiry: string | null = future, sandbox = true) {
  return {
    request_date_ms: now,
    subscriber: {
      entitlements: { aqualog_pro: { product_identifier: product, expires_date: expiry, grace_period_expires_date: null as string | null } },
      subscriptions: { [product]: { store: "play_store", is_sandbox: sandbox, expires_date: expiry, refunded_at: null as string | null, grace_period_expires_date: null as string | null, unsubscribe_detected_at: null as string | null } },
      non_subscriptions: { [product]: [{ store: "play_store", is_sandbox: sandbox, purchase_date: past, refunded_at: null as string | null }] },
    },
  };
}

Deno.test("RevenueCat annual entitlement uses server expiry including cancelled-but-paid access", () => {
  const value = customer();
  value.subscriber.subscriptions.aqualog_pro_annual.unsubscribe_detected_at = past;
  const result = interpretRevenueCatCustomer(value, true, now);
  assert(result.active && result.validUntilEpochMillis === Date.parse(future));
});
Deno.test("expired, refunded, foreign-store and cross-environment purchases cannot grant Pro", () => {
  const expired = customer("aqualog_pro_annual", past);
  assert(!interpretRevenueCatCustomer(expired, true, now).active);
  for (const kind of ["refund", "store", "environment"]) {
    const value = customer();
    const subscription = value.subscriber.subscriptions.aqualog_pro_annual;
    if (kind === "refund") subscription.refunded_at = past;
    if (kind === "store") subscription.store = "app_store";
    if (kind === "environment") subscription.is_sandbox = true;
    assert(!interpretRevenueCatCustomer(value, kind !== "environment", now).active, kind);
  }
});
Deno.test("grace period extends access only as far as both authoritative records agree", () => {
  const value = customer("aqualog_pro_annual", past);
  value.subscriber.entitlements.aqualog_pro.grace_period_expires_date = future;
  value.subscriber.subscriptions.aqualog_pro_annual.grace_period_expires_date = future;
  assert(interpretRevenueCatCustomer(value, true, now).active);
  value.subscriber.subscriptions.aqualog_pro_annual.grace_period_expires_date = null;
  assert(!interpretRevenueCatCustomer(value, true, now).active);
});
Deno.test("base-plan identifiers normalize to the canonical product", () => {
  const result = interpretRevenueCatCustomer(customer("aqualog_pro_annual:annual"), true, now);
  assert(result.active && result.productId === "aqualog_pro_annual");
});
Deno.test("lifetime requires a real non-subscription purchase and null expiry", () => {
  const value = customer("aqualog_pro_lifetime", null);
  assert(interpretRevenueCatCustomer(value, true, now).active);
  value.subscriber.non_subscriptions.aqualog_pro_lifetime = [];
  assert(!interpretRevenueCatCustomer(value, true, now).active);
  assert(!interpretRevenueCatCustomer(customer("aqualog_pro_lifetime", future), true, now).active);
});
Deno.test("refunded and wrong-environment lifetime purchases are inactive", () => {
  const value = customer("aqualog_pro_lifetime", null);
  value.subscriber.non_subscriptions.aqualog_pro_lifetime[0].refunded_at = past;
  assert(!interpretRevenueCatCustomer(value, true, now).active);
  assert(!interpretRevenueCatCustomer(customer("aqualog_pro_lifetime", null, true), false, now).active);
});
Deno.test("missing Pro entitlement revokes access; unknown products and annual null expiry do not grant", () => {
  const value = customer();
  delete (value.subscriber.entitlements as Record<string, unknown>).aqualog_pro;
  assert(!interpretRevenueCatCustomer(value, true, now).active);
  assert(!interpretRevenueCatCustomer(customer("other_product"), true, now).active);
  assert(!interpretRevenueCatCustomer(customer("aqualog_pro_annual", null), true, now).active);
});
Deno.test("malformed RevenueCat snapshots fail instead of granting or erasing state", () => {
  for (const value of [null, {}, { request_date_ms: now + 120_000 }, { ...customer(), subscriber: {} }]) {
    let failed = false;
    try { interpretRevenueCatCustomer(value, true, now); } catch { failed = true; }
    assert(failed);
  }
});
Deno.test("opaque customer IDs are stable, account scoped, and separated by environment secret", async () => {
  const secret = "a".repeat(64);
  const first = await revenueCatAppUserId("account-a", secret);
  assert(/^[0-9a-f]{64}$/.test(first));
  assert(first === await revenueCatAppUserId("account-a", secret));
  assert(first !== await revenueCatAppUserId("account-b", secret));
  assert(first !== await revenueCatAppUserId("account-a", "b".repeat(64)));
});
Deno.test("Pro HTTP boundary authenticates and never accepts claimed account IDs or receipt payloads", async () => {
  const accounts: string[] = [];
  const dependencies = {
    authenticatedAccountId: () => Promise.resolve("account-a"),
    identity: (id: string) => { accounts.push(id); return Promise.resolve("opaque"); },
    refresh: (id: string) => { accounts.push(id); return Promise.resolve({ active: false }); },
  };
  const request = (body: unknown) => new Request("https://edge.invalid", { method: "POST", body: JSON.stringify(body) });
  assert((await handleProRequest(request({ action: "purchaseIdentity" }), dependencies)).status === 200);
  assert((await handleProRequest(request({ action: "loadEntitlement" }), dependencies)).status === 200);
  assert(accounts.join() === "account-a,account-a");
  assert((await handleProRequest(request(null), dependencies)).status === 400);
  for (const body of [{ action: "loadEntitlement", accountId: "account-b" }, { productId: "aqualog_pro_annual", purchaseToken: "fake" }]) {
    assert((await handleProRequest(request(body), dependencies)).status === 400);
  }
  assert((await handleProRequest(request({ action: "loadEntitlement" }), { ...dependencies, authenticatedAccountId: () => Promise.resolve(null) })).status === 401);
});
Deno.test("RevenueCat API failure is retryable and cannot be reported as verified", async () => {
  const result = await handleProRequest(new Request("https://edge.invalid", { method: "POST", body: '{"action":"loadEntitlement"}' }), {
    authenticatedAccountId: () => Promise.resolve("a"), identity: () => Promise.resolve("id"),
    refresh: () => Promise.reject(new Error("upstream credentials must not leak")),
  });
  assert(result.status === 503 && !(await result.text()).includes("credentials"));
});
Deno.test("webhook authenticates, deduplicates accounts and re-reads state for every event", async () => {
  const id = "a".repeat(64);
  let reads = 0;
  const dependencies = {
    authorization: "Bearer webhook-secret",
    accountIds: (ids: string[]) => { assert(ids.join() === id); return Promise.resolve(["account-a", "account-a"]); },
    refresh: (_account: string) => { reads++; return Promise.resolve({ active: false }); },
  };
  const request = (authorization: string) => new Request("https://edge.invalid", {
    method: "POST", headers: { authorization },
    body: JSON.stringify({ event: { type: "RENEWAL", app_user_id: id, aliases: [id], expiration_at_ms: now + 99_999_999 } }),
  });
  assert((await handleRevenueCatWebhook(request("Bearer wrong"), dependencies)).status === 401 && reads === 0);
  assert((await handleRevenueCatWebhook(request(dependencies.authorization), dependencies)).status === 200);
  assert((await handleRevenueCatWebhook(request(dependencies.authorization), dependencies)).status === 200);
  assert(reads === 2);
});
Deno.test("webhook retries on reconciliation failure and ignores deleted or unknown accounts", async () => {
  const request = () => new Request("https://edge.invalid", { method: "POST", headers: { authorization: "secret" }, body: JSON.stringify({ event: { app_user_id: "a".repeat(64) } }) });
  const dependencies = { authorization: "secret", accountIds: () => Promise.resolve(["a"]), refresh: () => Promise.reject(new Error("unavailable")) };
  assert((await handleRevenueCatWebhook(request(), dependencies)).status === 503);
  assert((await handleRevenueCatWebhook(request(), { ...dependencies, accountIds: () => Promise.resolve([]) })).status === 200);
});

Deno.test("one backend accepts production purchases and restricts sandbox grants to approved test accounts", () => {
  assert(interpretRevenueCatCustomer(customer("aqualog_pro_annual", future, false), false, now).active);
  assert(!interpretRevenueCatCustomer(customer(), false, now).active);
  assert(interpretRevenueCatCustomer(customer(), true, now).active);
  assert(interpretRevenueCatCustomer(customer("aqualog_pro_lifetime", null, false), false, now).active);
});

Deno.test("notification retries never roll back reconciled rights or notify unknown accounts", async () => {
  const request = () => new Request("https://edge.invalid", { method: "POST", headers: { authorization: "secret" }, body: JSON.stringify({ event: { app_user_id: "a".repeat(64) } }) });
  let reads = 0; let notifications = 0;
  const dependencies = {
    authorization: "secret", accountIds: async () => ["a"],
    refresh: async () => { reads++; }, notify: async () => { notifications++; throw new Error("offline"); },
  };
  assert((await handleRevenueCatWebhook(request(), dependencies)).status === 503);
  assert(reads === 1 && notifications === 1);
  assert((await handleRevenueCatWebhook(request(), { ...dependencies, accountIds: async () => [] })).status === 200);
  assert(reads === 1 && notifications === 1);
});
