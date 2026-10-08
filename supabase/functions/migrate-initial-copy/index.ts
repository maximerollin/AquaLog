import { createClient } from "jsr:@supabase/supabase-js@2";
import { validateInitialCopy } from "./service.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return Response.json({ error: "Authentication required" }, { status: 401 });
  }

  try {
    const payload = validateInitialCopy(await request.json());
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonymousKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!supabaseUrl || !anonymousKey) throw new Error("Supabase runtime is not configured");

    const client = createClient(supabaseUrl, anonymousKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userData, error: userError } = await client.auth.getUser();
    if (userError || !userData.user) {
      return Response.json({ error: "Authentication required" }, { status: 401 });
    }
    if (payload.accountId !== userData.user.id) {
      return Response.json({ error: "accountId does not match the authenticated user" }, { status: 403 });
    }

    const { error } = await client.rpc("migrate_initial_copy", { p_copy: payload });
    if (error) throw error;
    return Response.json({ migrated: true });
  } catch (error) {
    if (error instanceof TypeError || error instanceof SyntaxError) {
      return Response.json({ error: error.message }, { status: 400 });
    }
    console.error("Initial copy migration failed", error);
    return Response.json({ error: "Initial copy migration failed" }, { status: 500 });
  }
});
