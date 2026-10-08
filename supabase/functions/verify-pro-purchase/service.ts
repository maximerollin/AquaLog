export const annualProProductId = "aqualog_pro_annual";

type SubscriptionLineItem = {
  productId?: string;
  expiryTime?: string;
};

type SubscriptionPurchase = {
  subscriptionState?: string;
  acknowledgementState?: string;
  latestOrderId?: string;
  lineItems?: SubscriptionLineItem[];
};

export type VerifiedSubscription = {
  active: boolean;
  productId: string;
  validUntilEpochMillis: number;
  validUntil: string;
  latestOrderId: string | null;
  requiresAcknowledgement: boolean;
};

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
  };
}

export async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
