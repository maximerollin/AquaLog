import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { feedbackRpcStatus, parseFeedbackRequest } from "./service.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }
  const feedback = parseFeedbackRequest(body);
  if (feedback === null) return new Response("Invalid feedback", { status: 400 });

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const authorization = request.headers.get("Authorization") ?? "";
  const common = {
    feedback_message: feedback.message,
    feedback_kind: feedback.kind,
    feedback_species_id: feedback.speciesId ?? null,
    feedback_app_version: feedback.appVersion ?? null,
    feedback_language: feedback.language ?? null,
  };

  let error: { code?: string; message: string } | null;
  if (authorization.startsWith("Bearer ")) {
    // verify_jwt is off: the token is checked here so anonymous calls can pass through.
    const client = createClient(supabaseUrl, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: authorization } },
    });
    const { data, error: userError } = await client.auth.getUser(authorization.slice("Bearer ".length));
    if (userError || !data.user) return new Response("Unauthorized", { status: 401 });
    ({ error } = await client.rpc("submit_product_feedback", common));
  } else {
    if (feedback.installId === undefined) return new Response("Invalid feedback", { status: 400 });
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!serviceRoleKey) return new Response("Feedback unavailable", { status: 500 });
    const admin = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } });
    ({ error } = await admin.rpc("submit_anonymous_feedback", {
      feedback_install_id: feedback.installId,
      ...common,
    }));
  }
  if (error) {
    const status = feedbackRpcStatus(error.code);
    return new Response(status === 500 ? "Feedback unavailable" : error.message, { status });
  }
  return new Response(null, { status: 204 });
});
