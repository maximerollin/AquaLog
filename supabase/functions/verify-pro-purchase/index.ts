import { revenueCatServer } from "../_shared/revenuecat.ts";
import { handleProRequest } from "./service.ts";

Deno.serve(async (request) => {
  try {
    return await handleProRequest(request, revenueCatServer());
  } catch (_error) {
    return Response.json({ error: "Pro verification is not configured" }, { status: 503 });
  }
});
