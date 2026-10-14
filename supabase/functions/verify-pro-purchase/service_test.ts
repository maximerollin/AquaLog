import {
  decryptPurchaseToken,
  encryptPurchaseToken,
  interpretSubscription,
  playAccountIdentifier,
  ProPurchaseVerificationDependencies,
  revalidateStoredSubscription,
  StoredProPurchase,
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
  const verified = interpretSubscription(
    activeSubscription,
    "aqualog_pro_annual",
  );
  if (!verified.active || verified.validUntilEpochMillis !== 1824120000000) {
    throw new Error(
      "the verified entitlement did not preserve the Play expiry",
    );
  }
  if (!verified.requiresAcknowledgement) {
    throw new Error("pending acknowledgement was ignored");
  }
});

Deno.test("pending and mismatched Play subscriptions cannot grant Pro", () => {
  const pending = interpretSubscription(
    { ...activeSubscription, subscriptionState: "SUBSCRIPTION_STATE_PENDING" },
    "aqualog_pro_annual",
  );
  if (pending.active) throw new Error("a pending purchase granted Pro");

  const mismatched = interpretSubscription(
    activeSubscription,
    "another-product",
  );
  if (mismatched.active) throw new Error("a different product granted Pro");
});

Deno.test("active grace and canceled states grant only while Google expiry is after server now", () => {
  const now = Date.parse("2027-01-01T00:00:00Z");
  const states = [
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
  ];
  for (const subscriptionState of states) {
    const future = interpretSubscription(
      {
        ...activeSubscription,
        subscriptionState,
        lineItems: [{
          productId: "aqualog_pro_annual",
          expiryTime: "2027-01-01T00:00:00.001Z",
        }],
      },
      "aqualog_pro_annual",
      now,
    );
    const exactNow = interpretSubscription(
      {
        ...activeSubscription,
        subscriptionState,
        lineItems: [{
          productId: "aqualog_pro_annual",
          expiryTime: "2027-01-01T00:00:00Z",
        }],
      },
      "aqualog_pro_annual",
      now,
    );
    const past = interpretSubscription(
      {
        ...activeSubscription,
        subscriptionState,
        lineItems: [{
          productId: "aqualog_pro_annual",
          expiryTime: "2026-12-31T23:59:59.999Z",
        }],
      },
      "aqualog_pro_annual",
      now,
    );
    if (!future.active || exactNow.active || past.active) {
      throw new Error(
        `${subscriptionState} ignored the server expiry boundary`,
      );
    }
  }
});

Deno.test("stolen and unbound purchases are rejected before claim or acknowledgement", async () => {
  const ownerAccountId = "10000000-0000-0000-0000-000000000011";
  const attackerAccountId = "10000000-0000-0000-0000-000000000012";
  const ownerIdentity = await playAccountIdentifier(
    ownerAccountId,
    "test-hmac-secret",
  );
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

  if (
    pending.kind !== "rejected" || first.kind !== "verified" ||
    replay.kind !== "verified"
  ) {
    throw new Error("pending or replay behavior is incorrect");
  }
  if (
    store.claims.size !== 1 || store.claims.get("replay-token") !== accountId
  ) {
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
  if (failed.kind !== "acknowledgement_failed") {
    throw new Error("ack failure was hidden");
  }
  if (
    store.claims.get("retry-token") !== accountId ||
    store.entitlements.has(accountId)
  ) {
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

Deno.test("stored purchase tokens are encrypted and recoverable only with the server secret", async () => {
  const encrypted = await encryptPurchaseToken(
    "purchase-token-secret",
    "test-hmac-secret",
  );
  if (encrypted.includes("purchase-token-secret")) {
    throw new Error("purchase token was stored in plaintext");
  }
  const decrypted = await decryptPurchaseToken(encrypted, "test-hmac-secret");
  if (decrypted !== "purchase-token-secret") {
    throw new Error("encrypted token could not be recovered");
  }
  let wrongSecretRejected = false;
  try {
    await decryptPurchaseToken(encrypted, "another-secret");
  } catch {
    wrongSecretRejected = true;
  }
  if (!wrongSecretRejected) {
    throw new Error("another secret decrypted the purchase token");
  }
});

Deno.test("server reload preserves canceled access until Google marks it expired", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  const identity = await playAccountIdentifier(accountId, "test-hmac-secret");
  const updates: Array<{ active: boolean; validUntil: string }> = [];
  const stored = await storedPurchase(accountId);

  const outcome = await revalidateStoredSubscription(accountId, stored, {
    hmacSecret: "test-hmac-secret",
    loadGoogleSubscription: async () => ({
      ...subscriptionFor(identity),
      subscriptionState: "SUBSCRIPTION_STATE_CANCELED",
    }),
    acknowledgePurchase: async () => true,
    updateEntitlement: async (update) => {
      updates.push({ active: update.active, validUntil: update.validUntil });
    },
  });

  if (
    outcome.kind !== "verified" || updates.length !== 1 || !updates[0].active
  ) {
    throw new Error(
      "a canceled but unexpired Play subscription lost access early",
    );
  }
});

Deno.test("expired revoked and refunded Play states atomically deactivate stored entitlements", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  const identity = await playAccountIdentifier(accountId, "test-hmac-secret");
  const stored = await storedPurchase(accountId);
  const inactiveStates = ["expired", "revoked", "refunded"];

  for (const state of inactiveStates) {
    const updates: boolean[] = [];
    const outcome = await revalidateStoredSubscription(accountId, stored, {
      hmacSecret: "test-hmac-secret",
      loadGoogleSubscription: async () => ({
        ...subscriptionFor(identity),
        subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
        canceledStateContext: state === "expired"
          ? { userInitiatedCancellation: {} }
          : { developerInitiatedCancellation: {} },
      }),
      acknowledgePurchase: async () => true,
      updateEntitlement: async (update) => {
        updates.push(update.active);
      },
    });
    if (outcome.kind !== "rejected" || updates.length !== 1 || updates[0]) {
      throw new Error(`${state} Play state did not deactivate the entitlement`);
    }
  }
});

Deno.test("Google errors fail closed without overwriting the last server record", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  let updateCount = 0;
  let failed = false;
  try {
    await revalidateStoredSubscription(
      accountId,
      await storedPurchase(accountId),
      {
        hmacSecret: "test-hmac-secret",
        loadGoogleSubscription: async () => {
          throw new Error("Google unavailable");
        },
        acknowledgePurchase: async () => true,
        updateEntitlement: async () => {
          updateCount += 1;
        },
      },
    );
  } catch {
    failed = true;
  }
  if (!failed || updateCount !== 0) {
    throw new Error("a transient Google error mutated the stored entitlement");
  }
});

Deno.test("server reload never grants a stored claim before acknowledgement succeeds", async () => {
  const accountId = "10000000-0000-0000-0000-000000000011";
  const identity = await playAccountIdentifier(accountId, "test-hmac-secret");
  let updateCount = 0;
  const outcome = await revalidateStoredSubscription(
    accountId,
    await storedPurchase(accountId),
    {
      hmacSecret: "test-hmac-secret",
      loadGoogleSubscription: async () => subscriptionFor(identity),
      acknowledgePurchase: async () => false,
      updateEntitlement: async () => {
        updateCount += 1;
      },
    },
  );
  if (outcome.kind !== "acknowledgement_failed" || updateCount !== 0) {
    throw new Error("an unacknowledged stored claim granted Pro");
  }
});

async function storedPurchase(accountId: string): Promise<StoredProPurchase> {
  return {
    accountId,
    productId: "aqualog_pro_annual",
    purchaseTokenHash: "stored-token-hash",
    purchaseTokenCiphertext: await encryptPurchaseToken(
      "stored-purchase-token",
      "test-hmac-secret",
    ),
  };
}

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
    if (owner !== undefined && owner !== accountId) {
      throw new Error("purchase belongs to another account");
    }
    this.claims.set(purchaseToken, accountId);
  }

  async activate(accountId: string, purchaseToken: string): Promise<void> {
    if (this.claims.get(purchaseToken) !== accountId) {
      throw new Error("purchase is not claimed");
    }
    this.entitlements.add(accountId);
  }
}

function dependenciesFor(
  store: FakePurchaseStore,
  google: FakeGooglePlay,
): ProPurchaseVerificationDependencies {
  return {
    hmacSecret: "test-hmac-secret",
    loadGoogleSubscription: (purchaseToken) =>
      google.subscription(purchaseToken),
    claimPurchase: ({ accountId, purchaseToken }) =>
      store.claim(accountId, purchaseToken),
    acknowledgePurchase: () => google.acknowledge(),
    activateEntitlement: ({ accountId, purchaseToken }) =>
      store.activate(accountId, purchaseToken),
  };
}
