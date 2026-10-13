export const annualProProductId = "aqualog_pro_annual";

type SubscriptionLineItem = {
  productId?: string;
  expiryTime?: string;
};

type SubscriptionPurchase = {
  subscriptionState?: string;
  acknowledgementState?: string;
  latestOrderId?: string;
  externalAccountIdentifiers?: {
    obfuscatedExternalAccountId?: string;
  };
  lineItems?: SubscriptionLineItem[];
};

export type VerifiedSubscription = {
  active: boolean;
  productId: string;
  validUntilEpochMillis: number;
  validUntil: string;
  latestOrderId: string | null;
  requiresAcknowledgement: boolean;
  obfuscatedExternalAccountId: string | null;
};

export type ProPurchaseClaim = {
  accountId: string;
  productId: string;
  purchaseToken: string;
  purchaseTokenHash: string;
  validUntil: string;
  latestOrderId: string | null;
};

export type ProPurchaseVerificationDependencies = {
  hmacSecret: string;
  loadGoogleSubscription: (purchaseToken: string) => Promise<unknown>;
  claimPurchase: (claim: ProPurchaseClaim) => Promise<void>;
  acknowledgePurchase: (claim: ProPurchaseClaim) => Promise<boolean>;
  activateEntitlement: (claim: ProPurchaseClaim) => Promise<void>;
};

export type ProPurchaseVerificationOutcome =
  | { kind: "verified"; subscription: VerifiedSubscription }
  | { kind: "rejected"; reason: string }
  | { kind: "acknowledgement_failed" };

export function validateVerificationRequest(value: unknown): {
  productId: string;
  purchaseToken: string;
} {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError("Purchase verification requires a JSON object");
  }
  const candidate = value as Record<string, unknown>;
  if (candidate.productId !== annualProProductId) {
    throw new TypeError("Unexpected Google Play product");
  }
  if (typeof candidate.purchaseToken !== "string" || candidate.purchaseToken.length < 8) {
    throw new TypeError("Purchase token is required");
  }
  return { productId: candidate.productId, purchaseToken: candidate.purchaseToken };
}

export function interpretSubscription(
  value: unknown,
  expectedProductId: string,
): VerifiedSubscription {
  const purchase = value as SubscriptionPurchase;
  const lineItem = purchase.lineItems?.find((item) => item.productId === expectedProductId);
  const expiry = lineItem?.expiryTime ? Date.parse(lineItem.expiryTime) : Number.NaN;
  const stateCanGrant = purchase.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
    purchase.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
  const active = expectedProductId === annualProProductId && stateCanGrant && Number.isFinite(expiry);
  return {
    active,
    productId: expectedProductId,
    validUntilEpochMillis: active ? expiry : 0,
    validUntil: active ? new Date(expiry).toISOString() : new Date(0).toISOString(),
    latestOrderId: purchase.latestOrderId ?? null,
    requiresAcknowledgement: purchase.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
    obfuscatedExternalAccountId:
      purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
  };
}

export async function playAccountIdentifier(accountId: string, hmacSecret: string): Promise<string> {
  if (!accountId || !hmacSecret) throw new TypeError("Purchase account identity is unavailable");
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(hmacSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`aqualog-play-account:v1:${accountId}`),
  );
  return hex(new Uint8Array(signature));
}

export async function verifyAndClaimSubscription(
  accountId: string,
  request: { productId: string; purchaseToken: string },
  dependencies: ProPurchaseVerificationDependencies,
): Promise<ProPurchaseVerificationOutcome> {
  const verified = interpretSubscription(
    await dependencies.loadGoogleSubscription(request.purchaseToken),
    request.productId,
  );
  if (!verified.active) return { kind: "rejected", reason: "subscription is not active" };
  const expectedIdentity = await playAccountIdentifier(accountId, dependencies.hmacSecret);
  if (verified.obfuscatedExternalAccountId !== expectedIdentity) {
    return { kind: "rejected", reason: "purchase is not bound to this account" };
  }
  const claim: ProPurchaseClaim = {
    accountId,
    productId: request.productId,
    purchaseToken: request.purchaseToken,
    purchaseTokenHash: await sha256(request.purchaseToken),
    validUntil: verified.validUntil,
    latestOrderId: verified.latestOrderId,
  };
  await dependencies.claimPurchase(claim);
  if (verified.requiresAcknowledgement && !(await dependencies.acknowledgePurchase(claim))) {
    return { kind: "acknowledgement_failed" };
  }
  await dependencies.activateEntitlement(claim);
  return { kind: "verified", subscription: verified };
}

export async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return hex(new Uint8Array(digest));
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
