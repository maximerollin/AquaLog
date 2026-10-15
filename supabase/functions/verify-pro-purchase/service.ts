export const annualProProductId = "aqualog_pro_annual";
export const lifetimeProProductId = "aqualog_pro_lifetime";

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
  validUntilEpochMillis: number | null;
  validUntil: string | null;
  latestOrderId: string | null;
  requiresAcknowledgement: boolean;
  obfuscatedExternalAccountId: string | null;
};

export type ProPurchaseClaim = {
  accountId: string;
  productId: string;
  purchaseToken: string;
  purchaseTokenHash: string;
  purchaseTokenCiphertext: string;
  validUntil: string | null;
  latestOrderId: string | null;
};

export type StoredProPurchase = {
  accountId: string;
  productId: string;
  purchaseTokenHash: string;
  purchaseTokenCiphertext: string;
};

export type ProEntitlementRevalidation = {
  accountId: string;
  productId: string;
  purchaseTokenHash: string;
  active: boolean;
  validUntil: string | null;
  latestOrderId: string | null;
};

export type ProEntitlementRevalidationDependencies = {
  hmacSecret: string;
  loadGoogleSubscription: (purchaseToken: string) => Promise<unknown>;
  acknowledgePurchase: (purchaseToken: string) => Promise<boolean>;
  updateEntitlement: (update: ProEntitlementRevalidation) => Promise<void>;
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
  if (
    typeof candidate.productId !== "string" ||
    ![annualProProductId, lifetimeProProductId].includes(candidate.productId)
  ) {
    throw new TypeError("Unexpected Google Play product");
  }
  if (
    typeof candidate.purchaseToken !== "string" ||
    candidate.purchaseToken.length < 8
  ) {
    throw new TypeError("Purchase token is required");
  }
  return {
    productId: candidate.productId,
    purchaseToken: candidate.purchaseToken,
  };
}

type LifetimePurchase = {
  purchaseState?: number;
  acknowledgementState?: number;
  productId?: string;
  orderId?: string;
  refundableQuantity?: number;
  obfuscatedExternalAccountId?: string;
};

export function interpretLifetimePurchase(
  value: unknown,
  expectedProductId: string,
): VerifiedSubscription {
  const purchase = value as LifetimePurchase;
  const active = expectedProductId === lifetimeProProductId &&
    purchase.productId === expectedProductId && purchase.purchaseState === 0 &&
    (purchase.refundableQuantity ?? 1) > 0;
  return {
    active,
    productId: expectedProductId,
    validUntilEpochMillis: null,
    validUntil: null,
    latestOrderId: purchase.orderId ?? null,
    requiresAcknowledgement: purchase.acknowledgementState === 0,
    obfuscatedExternalAccountId: purchase.obfuscatedExternalAccountId ?? null,
  };
}

export function interpretSubscription(
  value: unknown,
  expectedProductId: string,
  serverNowEpochMillis: number = Date.now(),
): VerifiedSubscription {
  const purchase = value as SubscriptionPurchase;
  const lineItem = purchase.lineItems?.find((item) =>
    item.productId === expectedProductId
  );
  const expiry = lineItem?.expiryTime
    ? Date.parse(lineItem.expiryTime)
    : Number.NaN;
  const stateCanGrant =
    purchase.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
    purchase.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" ||
    purchase.subscriptionState === "SUBSCRIPTION_STATE_CANCELED";
  const active = expectedProductId === annualProProductId && stateCanGrant &&
    Number.isFinite(expiry) && expiry > serverNowEpochMillis;
  return {
    active,
    productId: expectedProductId,
    validUntilEpochMillis: Number.isFinite(expiry) ? expiry : 0,
    validUntil: Number.isFinite(expiry)
      ? new Date(expiry).toISOString()
      : new Date(0).toISOString(),
    latestOrderId: purchase.latestOrderId ?? null,
    requiresAcknowledgement:
      purchase.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
    obfuscatedExternalAccountId:
      purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
  };
}

export async function playAccountIdentifier(
  accountId: string,
  hmacSecret: string,
): Promise<string> {
  if (!accountId || !hmacSecret) {
    throw new TypeError("Purchase account identity is unavailable");
  }
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
  const googlePurchase = await dependencies.loadGoogleSubscription(request.purchaseToken);
  const verified = request.productId === lifetimeProProductId
    ? interpretLifetimePurchase(googlePurchase, request.productId)
    : interpretSubscription(googlePurchase, request.productId);
  if (!verified.active) {
    return { kind: "rejected", reason: "subscription is not active" };
  }
  const expectedIdentity = await playAccountIdentifier(
    accountId,
    dependencies.hmacSecret,
  );
  if (verified.obfuscatedExternalAccountId !== expectedIdentity) {
    return {
      kind: "rejected",
      reason: "purchase is not bound to this account",
    };
  }
  const claim: ProPurchaseClaim = {
    accountId,
    productId: request.productId,
    purchaseToken: request.purchaseToken,
    purchaseTokenHash: await sha256(request.purchaseToken),
    purchaseTokenCiphertext: await encryptPurchaseToken(
      request.purchaseToken,
      dependencies.hmacSecret,
    ),
    validUntil: verified.validUntil,
    latestOrderId: verified.latestOrderId,
  };
  await dependencies.claimPurchase(claim);
  if (
    verified.requiresAcknowledgement &&
    !(await dependencies.acknowledgePurchase(claim))
  ) {
    return { kind: "acknowledgement_failed" };
  }
  await dependencies.activateEntitlement(claim);
  return { kind: "verified", subscription: verified };
}

export async function revalidateStoredSubscription(
  accountId: string,
  stored: StoredProPurchase,
  dependencies: ProEntitlementRevalidationDependencies,
): Promise<ProPurchaseVerificationOutcome> {
  if (
    stored.accountId !== accountId ||
    ![annualProProductId, lifetimeProProductId].includes(stored.productId)
  ) {
    return {
      kind: "rejected",
      reason: "stored purchase does not belong to this account",
    };
  }
  const purchaseToken = await decryptPurchaseToken(
    stored.purchaseTokenCiphertext,
    dependencies.hmacSecret,
  );
  const googlePurchase = await dependencies.loadGoogleSubscription(purchaseToken);
  const verified = stored.productId === lifetimeProProductId
    ? interpretLifetimePurchase(googlePurchase, stored.productId)
    : interpretSubscription(googlePurchase, stored.productId);
  const expectedIdentity = await playAccountIdentifier(
    accountId,
    dependencies.hmacSecret,
  );
  const active = verified.active &&
    verified.obfuscatedExternalAccountId === expectedIdentity;
  if (active && verified.requiresAcknowledgement) {
    if (!(await dependencies.acknowledgePurchase(purchaseToken))) {
      return { kind: "acknowledgement_failed" };
    }
  }
  await dependencies.updateEntitlement({
    accountId,
    productId: stored.productId,
    purchaseTokenHash: stored.purchaseTokenHash,
    active,
    validUntil: verified.validUntil,
    latestOrderId: verified.latestOrderId,
  });
  return active
    ? { kind: "verified", subscription: verified }
    : { kind: "rejected", reason: "subscription is not active" };
}

export async function encryptPurchaseToken(
  purchaseToken: string,
  secret: string,
): Promise<string> {
  if (!purchaseToken || !secret) {
    throw new TypeError("Purchase token encryption is unavailable");
  }
  const key = await purchaseTokenEncryptionKey(secret, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv },
      key,
      new TextEncoder().encode(purchaseToken),
    ),
  );
  return base64UrlBytes(concat(iv, ciphertext));
}

export async function decryptPurchaseToken(
  ciphertext: string,
  secret: string,
): Promise<string> {
  if (!ciphertext || !secret) {
    throw new TypeError("Stored purchase token is unavailable");
  }
  const bytes = base64UrlDecode(ciphertext);
  if (bytes.length <= 12) {
    throw new TypeError("Stored purchase token is invalid");
  }
  const key = await purchaseTokenEncryptionKey(secret, ["decrypt"]);
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: bytes.slice(0, 12) },
    key,
    bytes.slice(12),
  );
  return new TextDecoder().decode(plaintext);
}

export async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return hex(new Uint8Array(digest));
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes).map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function purchaseTokenEncryptionKey(
  secret: string,
  usages: KeyUsage[],
): Promise<CryptoKey> {
  const material = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`aqualog-play-token-encryption:v1:${secret}`),
  );
  return crypto.subtle.importKey(
    "raw",
    material,
    { name: "AES-GCM" },
    false,
    usages,
  );
}

function concat(first: Uint8Array, second: Uint8Array): Uint8Array {
  const result = new Uint8Array(first.length + second.length);
  result.set(first);
  result.set(second, first.length);
  return result;
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll(
    "=",
    "",
  );
}

function base64UrlDecode(value: string): Uint8Array {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(
    Math.ceil(value.length / 4) * 4,
    "=",
  );
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
