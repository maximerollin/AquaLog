export const annualProProductId = "aqualog_pro_annual";
export const lifetimeProProductId = "aqualog_pro_lifetime";
export const proEntitlementId = "aqualog_pro";

export type RevenueCatEntitlement = {
  active: boolean;
  productId: string | null;
  validUntilEpochMillis: number | null;
  observedAtEpochMillis: number;
};

function object(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError("Invalid RevenueCat customer response");
  }
  return value as Record<string, unknown>;
}

function date(value: unknown): number {
  return typeof value === "string" ? Date.parse(value) : NaN;
}

/** Interpret the authenticated RevenueCat API response, never data submitted by the Android client. */
export function interpretRevenueCatCustomer(
  value: unknown,
  sandbox: boolean,
  now = Date.now(),
): RevenueCatEntitlement {
  const response = object(value);
  const observedAt = response.request_date_ms;
  if (typeof observedAt !== "number" || !Number.isFinite(observedAt) || observedAt <= 0 || observedAt > now + 60_000) {
    throw new TypeError("RevenueCat response timestamp is invalid");
  }
  const subscriber = object(response.subscriber);
  const entitlements = object(subscriber.entitlements);
  const inactive: RevenueCatEntitlement = {
    active: false, productId: null, validUntilEpochMillis: null, observedAtEpochMillis: observedAt,
  };
  if (!(proEntitlementId in entitlements)) return inactive;
  const entitlement = object(entitlements[proEntitlementId]);
  const rawProductId = entitlement.product_identifier;
  if (typeof rawProductId !== "string") return inactive;
  // Google subscription product identifiers may include their base plan after a colon.
  const productId = rawProductId.split(":")[0];
  if (productId === annualProProductId) {
    const subscription = object(object(subscriber.subscriptions)[rawProductId]);
    if (subscription.store !== "play_store" || subscription.is_sandbox !== sandbox || subscription.refunded_at != null) return inactive;
    const expiry = Math.max(date(entitlement.expires_date), date(entitlement.grace_period_expires_date) || 0);
    const subscriptionExpiry = Math.max(date(subscription.expires_date), date(subscription.grace_period_expires_date) || 0);
    const until = Math.min(expiry, subscriptionExpiry);
    if (!Number.isFinite(until) || until <= now) return inactive;
    return { active: true, productId, validUntilEpochMillis: until, observedAtEpochMillis: observedAt };
  }
  if (productId === lifetimeProProductId && entitlement.expires_date === null) {
    const purchases = object(subscriber.non_subscriptions)[rawProductId];
    if (!Array.isArray(purchases)) return inactive;
    const owned = purchases.some((value) => {
      const purchase = object(value);
      return purchase.store === "play_store" && purchase.is_sandbox === sandbox && purchase.refunded_at == null &&
        Number.isFinite(date(purchase.purchase_date)) && date(purchase.purchase_date) <= now;
    });
    if (owned) return { active: true, productId, validUntilEpochMillis: null, observedAtEpochMillis: observedAt };
  }
  return inactive;
}

export async function revenueCatAppUserId(accountId: string, secret: string): Promise<string> {
  if (!accountId || secret.length < 32) throw new TypeError("RevenueCat identity is not configured");
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`aqualog-revenuecat:v1:${accountId}`));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export type ProEndpointDependencies = {
  authenticatedAccountId(request: Request): Promise<string | null>;
  identity(accountId: string): Promise<string>;
  refresh(accountId: string): Promise<unknown>;
};

export async function handleProRequest(request: Request, dependencies: ProEndpointDependencies): Promise<Response> {
  if (request.method !== "POST") return Response.json({ error: "method not allowed" }, { status: 405 });
  try {
    const accountId = await dependencies.authenticatedAccountId(request);
    if (!accountId) return Response.json({ error: "authentication required" }, { status: 401 });
    let body: Record<string, unknown>;
    try { body = object(await request.json()); } catch {
      return Response.json({ error: "invalid request" }, { status: 400 });
    }
    // No client-provided token, account ID, expiry or CustomerInfo can grant Pro.
    if (Object.keys(body).length !== 1) return Response.json({ error: "invalid request" }, { status: 400 });
    if (body.action === "purchaseIdentity") {
      return Response.json({ obfuscatedAccountId: await dependencies.identity(accountId) });
    }
    if (body.action === "loadEntitlement") return Response.json(await dependencies.refresh(accountId));
    return Response.json({ error: "unsupported purchase request" }, { status: 400 });
  } catch (_error) {
    return Response.json({ error: "Pro verification unavailable; try again" }, { status: 503 });
  }
}

export type WebhookDependencies = {
  authorization: string;
  accountIds(appUserIds: string[]): Promise<string[]>;
  refresh(accountId: string): Promise<unknown>;
};

/** Re-read authoritative state: duplicate and out-of-order events never apply event payload entitlements. */
export async function handleRevenueCatWebhook(request: Request, dependencies: WebhookDependencies): Promise<Response> {
  if (request.method !== "POST") return Response.json({ error: "method not allowed" }, { status: 405 });
  if (!dependencies.authorization || request.headers.get("authorization") !== dependencies.authorization) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  try {
    const event = object(object(await request.json()).event);
    if (event.type === "TEST") return Response.json({ received: true });
    const ids = [event.app_user_id, ...[event.aliases, event.transferred_from, event.transferred_to].flatMap((value) => Array.isArray(value) ? value : [])]
      .filter((id): id is string => typeof id === "string" && /^[0-9a-f]{64}$/.test(id));
    const accounts = await dependencies.accountIds([...new Set(ids)]);
    for (const account of new Set(accounts)) await dependencies.refresh(account);
    return Response.json({ received: true });
  } catch (_error) {
    return Response.json({ error: "RevenueCat update incomplete; retry" }, { status: 503 });
  }
}
