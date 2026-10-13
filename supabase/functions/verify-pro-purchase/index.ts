import {
  annualProProductId,
  playAccountIdentifier,
  ProPurchaseClaim,
  validateVerificationRequest,
  verifyAndClaimSubscription,
} from "./service.ts";

const jsonHeaders = { "content-type": "application/json" };

Deno.serve(async (request) => {
  if (request.method !== "POST") return response(405, { error: "method not allowed" });
  try {
    const supabaseUrl = requiredEnv("SUPABASE_URL");
    const anonKey = requiredEnv("SUPABASE_ANON_KEY");
    const serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
    const authorization = request.headers.get("authorization");
    if (!authorization?.startsWith("Bearer ")) return response(401, { error: "authentication required" });

    const accountId = await authenticatedAccountId(supabaseUrl, anonKey, authorization);
    const requestBody = await request.json();
    const hmacSecret = requiredEnv("GOOGLE_PLAY_ACCOUNT_HMAC_SECRET");
    if (isPurchaseIdentityRequest(requestBody)) {
      return response(200, {
        obfuscatedAccountId: await playAccountIdentifier(accountId, hmacSecret),
      });
    }
    const verificationRequest = validateVerificationRequest(requestBody);
    const serviceAccount = JSON.parse(requiredEnv("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"));
    const packageName = requiredEnv("GOOGLE_PLAY_PACKAGE_NAME");
    const googleAccessToken = await googleServiceAccessToken(serviceAccount);
    const outcome = await verifyAndClaimSubscription(accountId, verificationRequest, {
      hmacSecret,
      loadGoogleSubscription: async (purchaseToken) => {
        const purchaseResponse = await fetch(
          `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
          { headers: { authorization: `Bearer ${googleAccessToken}` } },
        );
        if (!purchaseResponse.ok) throw new GooglePurchaseRejectedError();
        return purchaseResponse.json();
      },
      claimPurchase: (claim) => callPurchaseRpc(
        supabaseUrl,
        serviceRoleKey,
        "claim_verified_pro_purchase",
        claim,
      ),
      acknowledgePurchase: async (claim) => {
        const acknowledgement = await fetch(
          `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(annualProProductId)}/tokens/${encodeURIComponent(claim.purchaseToken)}:acknowledge`,
          {
            method: "POST",
            headers: { ...jsonHeaders, authorization: `Bearer ${googleAccessToken}` },
            body: "{}",
          },
        );
        return acknowledgement.ok;
      },
      activateEntitlement: (claim) => callPurchaseRpc(
        supabaseUrl,
        serviceRoleKey,
        "activate_claimed_pro_purchase",
        claim,
      ),
    });
    if (outcome.kind === "rejected") return response(422, { active: false, error: outcome.reason });
    if (outcome.kind === "acknowledgement_failed") {
      return response(502, { error: "Google Play acknowledgement failed; retry verification" });
    }
    const verified = outcome.subscription;
    return response(200, {
      active: true,
      productId: verified.productId,
      validUntilEpochMillis: verified.validUntilEpochMillis,
    });
  } catch (error) {
    if (error instanceof AuthenticationError) return response(401, { error: "authentication required" });
    if (error instanceof GooglePurchaseRejectedError) {
      return response(422, { error: "Google Play did not verify this purchase" });
    }
    if (error instanceof TypeError) return response(400, { error: error.message });
    return response(500, { error: "purchase verification unavailable" });
  }
});

function isPurchaseIdentityRequest(value: unknown): boolean {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    (value as Record<string, unknown>).action === "purchaseIdentity";
}

async function callPurchaseRpc(
  supabaseUrl: string,
  serviceRoleKey: string,
  functionName: string,
  claim: ProPurchaseClaim,
): Promise<void> {
  const stored = await fetch(`${supabaseUrl}/rest/v1/rpc/${functionName}`, {
    method: "POST",
    headers: {
      ...jsonHeaders,
      apikey: serviceRoleKey,
      authorization: `Bearer ${serviceRoleKey}`,
    },
    body: JSON.stringify({
      p_account_id: claim.accountId,
      p_product_id: claim.productId,
      p_purchase_token_hash: claim.purchaseTokenHash,
      p_valid_until: claim.validUntil,
      p_latest_order_id: claim.latestOrderId,
    }),
  });
  if (!stored.ok) throw new Error("verified purchase ownership could not be stored");
}

async function authenticatedAccountId(
  supabaseUrl: string,
  anonKey: string,
  authorization: string,
): Promise<string> {
  const userResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, authorization },
  });
  if (!userResponse.ok) throw new AuthenticationError();
  const user = await userResponse.json();
  if (typeof user.id !== "string") throw new AuthenticationError();
  return user.id;
}

type GoogleServiceAccount = { client_email?: string; private_key?: string };

async function googleServiceAccessToken(serviceAccount: GoogleServiceAccount): Promise<string> {
  if (!serviceAccount.client_email || !serviceAccount.private_key) {
    throw new Error("Google Play service account is not configured");
  }
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const signingInput = `${header}.${claims}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const assertion = `${signingInput}.${base64Url(new Uint8Array(signature))}`;
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenResponse.ok) throw new Error("Google Play authorization failed");
  const token = await tokenResponse.json();
  if (typeof token.access_token !== "string") throw new Error("Google Play returned no access token");
  return token.access_token;
}

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const binary = atob(pem.replace(/-----[^-]+-----|\s/g, ""));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0)).buffer;
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`${name} is required`);
  return value;
}

class AuthenticationError extends Error {}
class GooglePurchaseRejectedError extends Error {}

function response(status: number, body: Record<string, unknown>): Response {
  if (body.error === "authentication required") status = 401;
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}
