import { requiredEnv, revenueCatServer } from "../_shared/revenuecat.ts";
import { handleRevenueCatWebhook } from "../verify-pro-purchase/service.ts";

Deno.serve(async (request) => {
  try {
    return await handleRevenueCatWebhook(request, {
      ...revenueCatServer(), authorization: requiredEnv("REVENUECAT_WEBHOOK_AUTHORIZATION"),
    });
  } catch (_error) {
    return Response.json({ error: "RevenueCat webhook is not configured" }, { status: 503 });
  }
});
