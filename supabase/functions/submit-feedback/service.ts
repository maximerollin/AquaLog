export function validateFeedbackMessage(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 && value.length <= 2000 ? value : null;
}

export function feedbackRpcStatus(code: string | undefined): number {
  if (code === "42501") return 401;
  if (code === "P0001") return 429;
  return 500;
}
