import { createClient } from "jsr:@supabase/supabase-js@2";
import { AccountDeletionAdmin, deleteAccount } from "./service.ts";

const json = (body: unknown, status: number) => Response.json(body, { status });

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Authentication required" }, 401);
  const accessToken = authorization.slice("Bearer ".length);
  let operationKey: string;
  try {
    const body = await request.json() as { operationKey?: unknown };
    if (typeof body.operationKey !== "string") throw new TypeError();
    operationKey = body.operationKey;
  } catch {
    return json({ error: "A valid deletion operation key is required" }, 400);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceRoleKey) return json({ error: "Account deletion is not configured" }, 503);
  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const buckets = (Deno.env.get("AQUALOG_ACCOUNT_STORAGE_BUCKETS") ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean);
  const admin: AccountDeletionAdmin = {
    async completedAccountId(key) {
      const { data, error } = await adminClient.from("account_deletion_receipts")
        .select("account_id").eq("operation_key", key).maybeSingle();
      if (error) throw error;
      return data?.account_id ?? null;
    },
    async authUserExists(accountId) {
      const { data, error } = await adminClient.auth.admin.getUserById(accountId);
      if (!error) return data.user != null;
      if ((error as { status?: number }).status === 404) return false;
      throw error;
    },
    async authenticatedAccountId() {
      const { data, error } = await userClient.auth.getUser();
      return error ? null : data.user?.id ?? null;
    },
    async deleteFiles(accountId) {
      for (const bucket of buckets) {
        let offset = 0;
        while (true) {
          const { data, error } = await adminClient.storage.from(bucket).list(accountId, { limit: 100, offset });
          if (error) throw error;
          const paths = (data ?? []).filter((item) => item.id).map((item) => `${accountId}/${item.name}`);
          if (paths.length > 0) {
            const { error: removeError } = await adminClient.storage.from(bucket).remove(paths);
            if (removeError) throw removeError;
          }
          if ((data?.length ?? 0) < 100) break;
          offset += 100;
        }
      }
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
    const outcome = await deleteAccount(admin, accessToken, operationKey);
    switch (outcome.status) {
      case "completed": return json({ deleted: true }, 200);
      case "authentication_required": return json({ error: "Authentication required" }, 401);
      case "retryable_failure": return json({ error: "Account deletion is incomplete", step: outcome.step }, 503);
    }
  } catch (error) {
    if (error instanceof TypeError) return json({ error: error.message }, 400);
    console.error("Account deletion failed", error);
    return json({ error: "Account deletion is incomplete" }, 503);
  }
});
