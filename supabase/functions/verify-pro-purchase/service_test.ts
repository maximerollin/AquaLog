import {
  interpretSubscription,
  playAccountIdentifier,
  ProPurchaseVerificationDependencies,
  verifyAndClaimSubscription,
} from "./service.ts";

const activeSubscription = {
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
  latestOrderId: "GPA.1234-5678-9012-34567",
  lineItems: [{
    productId: "aqualog_pro_annual",
    expiryTime: "2027-10-21T12:00:00Z",
  }],
};

Deno.test("an active matching Play subscription can grant Pro", () => {
  const verified = interpretSubscription(activeSubscription, "aqualog_pro_annual");
  if (!verified.active || verified.validUntilEpochMillis !== 1824120000000) {
    throw new Error("the verified entitlement did not preserve the Play expiry");
  }
  if (!verified.requiresAcknowledgement) throw new Error("pending acknowledgement was ignored");
});

Deno.test("pending and mismatched Play subscriptions cannot grant Pro", () => {
  const pending = interpretSubscription(
    { ...activeSubscription, subscriptionState: "SUBSCRIPTION_STATE_PENDING" },
    "aqualog_pro_annual",
  );
  if (pending.active) throw new Error("a pending purchase granted Pro");

  const mismatched = interpretSubscription(activeSubscription, "another-product");
  if (mismatched.active) throw new Error("a different product granted Pro");
});

Deno.test("stolen and unbound purchases are rejected before claim or acknowledgement", async () => {
  const ownerAccountId = "10000000-0000-0000-0000-000000000011";
  const attackerAccountId = "10000000-0000-0000-0000-000000000012";
  const ownerIdentity = await playAccountIdentifier(ownerAccountId, "test-hmac-secret");
  const store = new FakePurchaseStore();
  const google = new FakeGooglePlay({
    stolen: subscriptionFor(ownerIdentity),
    unbound: subscriptionFor(undefined),
  });
  const dependencies = dependenciesFor(store, google);

  const stolen = await verifyAndClaimSubscription(
    attackerAccountId,
    { productId: "aqualog_pro_annual", purchaseToken: "stolen-token" },
    dependencies,
  );
  const unbound = await verifyAndClaimSubscription(
    ownerAccountId,
    { productId: "aqualog_pro_annual", purchaseToken: "unbound-token" },
    dependencies,
  );

  if (stolen.kind !== "rejected" || unbound.kind !== "rejected") {
    throw new Error("an unbound or stolen purchase was accepted");
  }
  if (store.claims.size !== 0 || google.acknowledgementCount !== 0) {
    throw new Error("a rejected purchase reached claim or acknowledgement");
  }
});

Deno.test("pending purchases are rejected while verified replays keep one owner", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  const identity = await playAccountIdentifier(accountId, "test-hmac-secret");
  const store = new FakePurchaseStore();
  const google = new FakeGooglePlay({
    pending: {
      ...subscriptionFor(identity),
      subscriptionState: "SUBSCRIPTION_STATE_PENDING",
    },
    replay: subscriptionFor(identity),
  });
  const dependencies = dependenciesFor(store, google);

  const pending = await verifyAndClaimSubscription(
    accountId,
    { productId: "aqualog_pro_annual", purchaseToken: "pending-token" },
    dependencies,
  );
  const first = await verifyAndClaimSubscription(
    accountId,
    { productId: "aqualog_pro_annual", purchaseToken: "replay-token" },
    dependencies,
  );
  const replay = await verifyAndClaimSubscription(
    accountId,
    { productId: "aqualog_pro_annual", purchaseToken: "replay-token" },
    dependencies,
  );

  if (pending.kind !== "rejected" || first.kind !== "verified" || replay.kind !== "verified") {
    throw new Error("pending or replay behavior is incorrect");
  }
  if (store.claims.size !== 1 || store.claims.get("replay-token") !== accountId) {
    throw new Error("replay changed purchase ownership");
  }
});

Deno.test("acknowledgement failure keeps the safe claim and grants only after retry", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  const identity = await playAccountIdentifier(accountId, "test-hmac-secret");
  const store = new FakePurchaseStore();
  const google = new FakeGooglePlay({ retry: subscriptionFor(identity) });
  google.failNextAcknowledgement = true;
  const dependencies = dependenciesFor(store, google);

  const failed = await verifyAndClaimSubscription(
    accountId,
    { productId: "aqualog_pro_annual", purchaseToken: "retry-token" },
    dependencies,
  );
  if (failed.kind !== "acknowledgement_failed") throw new Error("ack failure was hidden");
  if (store.claims.get("retry-token") !== accountId || store.entitlements.has(accountId)) {
    throw new Error("ack failure lost ownership or granted Pro");
  }

  const retried = await verifyAndClaimSubscription(
    accountId,
    { productId: "aqualog_pro_annual", purchaseToken: "retry-token" },
    dependencies,
  );
  if (retried.kind !== "verified" || !store.entitlements.has(accountId)) {
    throw new Error("safe claimed purchase did not recover on retry");
  }
});

function subscriptionFor(obfuscatedExternalAccountId: string | undefined) {
  return {
    ...activeSubscription,
    externalAccountIdentifiers: obfuscatedExternalAccountId === undefined
      ? undefined
      : { obfuscatedExternalAccountId },
  };
}

class FakeGooglePlay {
  acknowledgementCount = 0;
  failNextAcknowledgement = false;

  constructor(private readonly subscriptions: Record<string, unknown>) {}

  async subscription(purchaseToken: string): Promise<unknown> {
    return this.subscriptions[purchaseToken.replace("-token", "")];
  }

  async acknowledge(): Promise<boolean> {
    this.acknowledgementCount += 1;
    if (this.failNextAcknowledgement) {
      this.failNextAcknowledgement = false;
      return false;
    }
    return true;
  }
}

class FakePurchaseStore {
  readonly claims = new Map<string, string>();
  readonly entitlements = new Set<string>();

  async claim(accountId: string, purchaseToken: string): Promise<void> {
    const owner = this.claims.get(purchaseToken);
    if (owner !== undefined && owner !== accountId) throw new Error("purchase belongs to another account");
    this.claims.set(purchaseToken, accountId);
  }

  async activate(accountId: string, purchaseToken: string): Promise<void> {
    if (this.claims.get(purchaseToken) !== accountId) throw new Error("purchase is not claimed");
    this.entitlements.add(accountId);
  }
}

function dependenciesFor(
  store: FakePurchaseStore,
  google: FakeGooglePlay,
): ProPurchaseVerificationDependencies {
  return {
    hmacSecret: "test-hmac-secret",
    loadGoogleSubscription: (purchaseToken) => google.subscription(purchaseToken),
    claimPurchase: ({ accountId, purchaseToken }) => store.claim(accountId, purchaseToken),
    acknowledgePurchase: () => google.acknowledge(),
    activateEntitlement: ({ accountId, purchaseToken }) => store.activate(accountId, purchaseToken),
  };
}
