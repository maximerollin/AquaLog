import { interpretSubscription } from "./service.ts";

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
