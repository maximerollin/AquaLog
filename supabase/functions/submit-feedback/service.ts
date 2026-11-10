export type FeedbackRequest = {
  message: string;
  kind: string;
  speciesId?: string;
  appVersion?: string;
  language?: string;
  installId?: string;
};

const KINDS = ["bug", "translation", "species", "idea", "other"];
const SPECIES_ID = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const LANGUAGE = /^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function validateFeedbackMessage(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 && value.length <= 2000 ? value : null;
}

/** Parses the request body, mirroring the database formats. Returns null when any field is invalid. */
export function parseFeedbackRequest(body: unknown): FeedbackRequest | null {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const raw = body as Record<string, unknown>;
  const message = validateFeedbackMessage(raw.message);
  if (message === null) return null;
  const result: FeedbackRequest = { message, kind: "other" };
  if (raw.kind !== undefined && raw.kind !== null) {
    if (typeof raw.kind !== "string" || !KINDS.includes(raw.kind)) return null;
    result.kind = raw.kind;
  }
  const optional = (
    key: "speciesId" | "appVersion" | "language" | "installId",
    valid: (v: string) => boolean,
  ): boolean => {
    const value = raw[key];
    if (value === undefined || value === null) return true;
    if (typeof value !== "string" || !valid(value)) return false;
    result[key] = value;
    return true;
  };
  if (
    !optional("speciesId", (v) => v.length <= 64 && SPECIES_ID.test(v)) ||
    !optional("appVersion", (v) => v.length >= 1 && v.length <= 32) ||
    !optional("language", (v) => v.length >= 2 && v.length <= 16 && LANGUAGE.test(v)) ||
    !optional("installId", (v) => UUID.test(v))
  ) return null;
  return result;
}

export function feedbackRpcStatus(code: string | undefined): number {
  if (code === "42501") return 401;
  if (code === "P0001") return 429;
  if (code === "22023") return 400;
  return 500;
}

/** null: token valid. 401: the auth server rejected it. 500: any other failure (outage), never a 401. */
export function userTokenStatus(error: { status?: number; name?: string } | null): 401 | 500 | null {
  if (error === null) return null;
  return error.status === 401 || error.status === 403 ? 401 : 500;
}
