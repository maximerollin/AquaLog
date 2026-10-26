import { createClient } from "jsr:@supabase/supabase-js@2";
import { interpretRevenueCatCustomer, revenueCatAppUserId } from "../verify-pro-purchase/service.ts";

export function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error("Server configuration is incomplete");
  return value;
}

export function revenueCatServer() {
  const url = requiredEnv("SUPABASE_URL");
  const anonKey = requiredEnv("SUPABASE_ANON_KEY");
  const admin = createClient(url, requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const environment = requiredEnv("REVENUECAT_ENVIRONMENT");
  if (environment !== "sandbox" && environment !== "production") throw new Error("Invalid RevenueCat environment");
  const apiKey = requiredEnv("REVENUECAT_SECRET_API_KEY");
  if (!apiKey.startsWith("sk_")) throw new Error("A secret RevenueCat V1 API key is required");
  const secret = requiredEnv("REVENUECAT_APP_USER_ID_SECRET");

  async function identity(accountId: string): Promise<string> {
    const id = await revenueCatAppUserId(accountId, secret);
    const { error } = await admin.rpc("register_revenuecat_customer", { p_account_id: accountId, p_app_user_id: id });
    if (error) throw new Error("RevenueCat account registration failed");
    return id;
  }
  async function refresh(accountId: string): Promise<unknown> {
    const id = await identity(accountId);
    const response = await fetch(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(id)}`, {
      headers: { authorization: `Bearer ${apiKey}`, "content-type": "application/json" },
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) throw new Error("RevenueCat is unavailable");
    const entitlement = interpretRevenueCatCustomer(await response.json(), environment === "sandbox");
    const { data, error } = await admin.rpc("sync_revenuecat_entitlement", {
      p_account_id: accountId,
      p_app_user_id: id,
      p_product_id: entitlement.productId,
      p_valid_until: entitlement.validUntilEpochMillis === null ? null : new Date(entitlement.validUntilEpochMillis).toISOString(),
      p_observed_at: new Date(entitlement.observedAtEpochMillis).toISOString(),
    });
    if (error) throw new Error("RevenueCat entitlement could not be stored");
    return data;
  }
  return {
    identity, refresh,
    async authenticatedAccountId(request: Request): Promise<string | null> {
      const authorization = request.headers.get("authorization");
      if (!authorization?.startsWith("Bearer ")) return null;
      const client = createClient(url, anonKey, {
        global: { headers: { authorization } }, auth: { persistSession: false, autoRefreshToken: false },
      });
      const { data, error } = await client.auth.getUser();
      return error ? null : data.user?.id ?? null;
    },
    async accountIds(appUserIds: string[]): Promise<string[]> {
      if (!appUserIds.length) return [];
      const { data, error } = await admin.from("revenuecat_customers").select("account_id").in("app_user_id", appUserIds).eq("deletion_requested", false);
      if (error) throw new Error("RevenueCat account lookup failed");
      return data.map((row) => row.account_id);
    },
  };
}
