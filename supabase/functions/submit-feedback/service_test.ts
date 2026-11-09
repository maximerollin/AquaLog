import { feedbackRpcStatus, parseFeedbackRequest } from "./service.ts";

const uuid = "123e4567-e89b-12d3-a456-426614174000";

Deno.test("a minimal body defaults the kind to other", () => {
  const parsed = parseFeedbackRequest({ message: "x" });
  if (JSON.stringify(parsed) !== JSON.stringify({ message: "x", kind: "other" })) {
    throw new Error("minimal body mismatch: " + JSON.stringify(parsed));
  }
});

Deno.test("a full valid body round-trips and keeps the message untrimmed", () => {
  const body = {
    message: "  useful feedback  ",
    kind: "species",
    speciesId: "neon-tetra",
    appVersion: "1.2.3",
    language: "fr-FR",
    installId: uuid,
  };
  if (JSON.stringify(parseFeedbackRequest(body)) !== JSON.stringify(body)) throw new Error("round trip failed");
});

Deno.test("every invalid field rejects the whole request", () => {
  const invalid: Record<string, unknown>[] = [
    { message: "x", kind: "spam" },
    { message: "x", speciesId: "Neon!" },
    { message: "x", appVersion: "v".repeat(33) },
    { message: "x", language: "français" },
    { message: "x", installId: "not-a-uuid" },
    { message: "   " },
    { message: "" },
    { message: "x".repeat(2001) },
    { message: 42 },
  ];
  for (const body of invalid) {
    if (parseFeedbackRequest(body) !== null) throw new Error("accepted " + JSON.stringify(body).slice(0, 80));
  }
  for (const body of [null, "x", 3, []]) {
    if (parseFeedbackRequest(body) !== null) throw new Error("accepted non-object");
  }
});

Deno.test("RPC errors expose only stable HTTP outcomes", () => {
  if (feedbackRpcStatus("42501") !== 401) throw new Error("auth mapping changed");
  if (feedbackRpcStatus("P0001") !== 429) throw new Error("rate mapping changed");
  if (feedbackRpcStatus("22023") !== 400) throw new Error("input mapping changed");
  if (feedbackRpcStatus("XX000") !== 500) throw new Error("internal errors must fail closed");
});
