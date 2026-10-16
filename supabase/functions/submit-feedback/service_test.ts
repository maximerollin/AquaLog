import { feedbackRpcStatus, validateFeedbackMessage } from "./service.ts";

Deno.test("feedback validation accepts only intentional bounded text", () => {
  if (validateFeedbackMessage("  useful feedback  ") !== "  useful feedback  ") throw new Error("valid message rejected");
  for (const invalid of [null, 42, "", "   ", "x".repeat(2001)]) {
    if (validateFeedbackMessage(invalid) !== null) throw new Error("invalid message accepted");
  }
});

Deno.test("RPC errors expose only stable HTTP outcomes", () => {
  if (feedbackRpcStatus("42501") !== 401) throw new Error("auth mapping changed");
  if (feedbackRpcStatus("P0001") !== 429) throw new Error("rate mapping changed");
  if (feedbackRpcStatus("XX000") !== 500) throw new Error("internal errors must fail closed");
});
