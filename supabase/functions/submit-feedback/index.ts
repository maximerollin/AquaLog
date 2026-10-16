import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { feedbackRpcStatus, validateFeedbackMessage } from "./service.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return new Response("Unauthorized", { status: 401 });
  let message: unknown;
  try {
    message = (await request.json()).message;
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }
  const validatedMessage = validateFeedbackMessage(message);
  if (validatedMessage === null) {
    return new Response("Invalid feedback", { status: 400 });
  }
  const client = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authorization } } },
  );
  const { error } = await client.rpc("submit_product_feedback", { feedback_message: validatedMessage });
  if (error) {
    const status = feedbackRpcStatus(error.code);
    return new Response(status === 500 ? "Feedback unavailable" : error.message, { status });
  }
  return new Response(null, { status: 204 });
});
