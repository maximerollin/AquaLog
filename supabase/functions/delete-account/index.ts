import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  AccountDeletionAdmin,
  deleteAccount,
  deleteAccountFiles,
  issueDeletionCredential,
  verifyDeletionCredential,
} from "./service.ts";

const json = (body: unknown, status: number) => Response.json(body, { status });

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const authorization = request.headers.get("Authorization");
  const accessToken = authorization?.startsWith("Bearer ") ? authorization.slice("Bearer ".length) : "";
  let operationKey: string;
  let recoveryCredential: string | null;
  try {
    const body = await request.json() as { operationKey?: unknown; recoveryCredential?: unknown };
    if (typeof body.operationKey !== "string") throw new TypeError();
    operationKey = body.operationKey;
    recoveryCredential = typeof body.recoveryCredential === "string" ? body.recoveryCredential : null;
  } catch {
    return json({ error: "A valid deletion operation key is required" }, 400);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const signingSecret = Deno.env.get("AQUALOG_DELETION_SIGNING_SECRET");
  if (!url || !anonKey || !serviceRoleKey || !signingSecret) {
    return json({ error: "Account deletion is not configured" }, 503);
  }
  const userClient = createClient(url, anonKey, {
    global: { headers: authorization ? { Authorization: authorization } : {} },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const buckets = (Deno.env.get("AQUALOG_ACCOUNT_STORAGE_BUCKETS") ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean);
  const { data: liveUserData, error: liveUserError } = accessToken
    ? await userClient.auth.getUser()
    : { data: { user: null }, error: null };
  const liveAccountId = liveUserError ? null : liveUserData.user?.id ?? null;
  if (!recoveryCredential) {
    if (!liveAccountId) return json({ error: "Authentication required" }, 401);
    const credential = await issueDeletionCredential(
      signingSecret,
      liveAccountId,
      operationKey,
      Math.floor(Date.now() / 1000) + 7 * 24 * 60 * 60,
    );
    return json({ recoveryCredential: credential }, 202);
  }
  const claims = await verifyDeletionCredential(
    signingSecret,
    recoveryCredential,
    operationKey,
    Math.floor(Date.now() / 1000),
  );
  if (!claims) return json({ error: "Deletion recovery credential is invalid" }, 401);
  const accountId = claims.accountId;
  if (liveAccountId && liveAccountId !== accountId) return json({ error: "Account mismatch" }, 403);

  const admin: AccountDeletionAdmin = {
    async isCompleted(ownerId, key) {
      const { data, error } = await adminClient.from("account_deletion_receipts")
        .select("account_id").eq("account_id", ownerId).eq("operation_key", key).maybeSingle();
      if (error) throw error;
      return data != null;
    },
    async authUserExists(accountId) {
      const { data, error } = await adminClient.auth.admin.getUserById(accountId);
      if (!error) return data.user != null;
      if ((error as { status?: number }).status === 404) return false;
      throw error;
    },
    async deleteFiles(accountId) {
      await deleteAccountFiles({
        async list(bucket, prefix, limit) {
          const { data, error } = await adminClient.storage.from(bucket).list(prefix, { limit, offset: 0 });
          if (error) throw error;
          return (data ?? []).map((item) => ({ name: item.name, id: item.id ?? null }));
        },
        async remove(bucket, paths) {
          const { error } = await adminClient.storage.from(bucket).remove(paths);
          if (error) throw error;
        },
      }, buckets, accountId);
    },
    async deleteBillingData(accountId) {
      const { data, error } = await adminClient.from("revenuecat_customers")
        .select("app_user_id").eq("account_id", accountId).maybeSingle();
      if (error) throw new Error("Billing data lookup failed");
      if (!data) return;
      const { error: freezeError } = await adminClient.from("revenuecat_customers")
        .update({ deletion_requested: true }).eq("account_id", accountId);
      if (freezeError) throw new Error("RevenueCat account cleanup could not be reserved");
      const key = Deno.env.get("REVENUECAT_SECRET_API_KEY");
      if (!key?.startsWith("sk_")) throw new Error("RevenueCat deletion is not configured");
      const response = await fetch(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(data.app_user_id)}`, {
        method: "DELETE", headers: { authorization: `Bearer ${key}` }, signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok && response.status !== 404) throw new Error("RevenueCat deletion is incomplete");
    },
    async deleteDatabaseData(_token, key) {
      const { error } = await userClient.rpc("delete_account_data", { p_operation_key: key });
      if (error) throw error;
    },
    async deleteAuthUser(accountId) {
      const { error } = await adminClient.auth.admin.deleteUser(accountId);
      if (error) throw error;
    },
  };

  try {
    const outcome = await deleteAccount(
      admin,
      accountId,
      liveAccountId,
      accessToken,
      operationKey,
    );
    switch (outcome.status) {
      case "completed": return json({ deleted: true }, 200);
      case "authentication_required": return json({ error: "Authentication required" }, 401);
      case "retryable_failure": return json({ error: "Account deletion is incomplete", step: outcome.step }, 503);
    }
  } catch (error) {
    if (error instanceof TypeError) return json({ error: error.message }, 400);
    console.error("Account deletion failed");
    return json({ error: "Account deletion is incomplete" }, 503);
  }
});
