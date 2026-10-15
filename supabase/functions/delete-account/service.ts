export interface AccountDeletionAdmin {
  isCompleted(accountId: string, operationKey: string): Promise<boolean>;
  authUserExists(accountId: string): Promise<boolean>;
  deleteFiles(accountId: string): Promise<void>;
  deleteDatabaseData(accessToken: string, operationKey: string): Promise<void>;
  deleteAuthUser(accountId: string): Promise<void>;
}

export interface AccountFileStorage {
  list(bucket: string, prefix: string, limit: number): Promise<Array<{ name: string; id: string | null }>>;
  remove(bucket: string, paths: string[]): Promise<void>;
}

export type AccountDeletionOutcome =
  | { status: "completed"; accountId: string }
  | { status: "authentication_required" }
  | { status: "retryable_failure"; step: "files" | "database" | "auth" };

export async function deleteAccount(
  admin: AccountDeletionAdmin,
  accountId: string,
  liveAuthenticatedAccountId: string | null,
  accessToken: string,
  operationKey: string,
): Promise<AccountDeletionOutcome> {
  if (!operationKey.trim() || operationKey.length > 200) throw new TypeError("A valid deletion operation key is required");
  if (await admin.isCompleted(accountId, operationKey)) {
    try { await admin.deleteFiles(accountId); } catch { return { status: "retryable_failure", step: "files" }; }
    if (!await admin.authUserExists(accountId)) return { status: "completed", accountId };
    try {
      await admin.deleteAuthUser(accountId);
      return { status: "completed", accountId };
    } catch {
      return { status: "retryable_failure", step: "auth" };
    }
  }
  if (liveAuthenticatedAccountId !== accountId) return { status: "authentication_required" };
  try { await admin.deleteFiles(accountId); } catch { return { status: "retryable_failure", step: "files" }; }
  try { await admin.deleteDatabaseData(accessToken, operationKey); } catch { return { status: "retryable_failure", step: "database" }; }
  try { await admin.deleteAuthUser(accountId); } catch { return { status: "retryable_failure", step: "auth" }; }
  return { status: "completed", accountId };
}

export async function deleteAccountFiles(
  storage: AccountFileStorage,
  buckets: string[],
  accountId: string,
  maximumObjects = 10_000,
  maximumDepth = 16,
): Promise<void> {
  let deleted = 0;
  for (const bucket of buckets) {
    const drain = async (prefix: string, depth: number): Promise<void> => {
      if (depth > maximumDepth) throw new Error("Account storage nesting limit exceeded");
      while (true) {
        const page = await storage.list(bucket, prefix, 100);
        if (page.length === 0) break;
        const objects = page.filter((item) => item.id !== null).map((item) => `${prefix}/${item.name}`);
        const folders = page.filter((item) => item.id === null).map((item) => `${prefix}/${item.name}`);
        for (const folder of folders) await drain(folder, depth + 1);
        if (objects.length > 0) {
          deleted += objects.length;
          if (deleted > maximumObjects) throw new Error("Account storage object limit exceeded");
          await storage.remove(bucket, objects);
        }
        if (objects.length === 0 && folders.length === 0) throw new Error("Account storage listing made no progress");
      }
    };
    await drain(accountId, 0);
  }
}

const encoder = new TextEncoder();
const toBase64Url = (bytes: Uint8Array) => {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
};
const fromBase64Url = (value: string) => {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
};
const signingKey = (secret: string) => crypto.subtle.importKey(
  "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"],
);

export async function issueDeletionCredential(
  secret: string,
  accountId: string,
  operationKey: string,
  expiresAtEpochSeconds: number,
): Promise<string> {
  const payload = toBase64Url(encoder.encode(JSON.stringify({ v: 1, accountId, operationKey, exp: expiresAtEpochSeconds })));
  const signature = await crypto.subtle.sign("HMAC", await signingKey(secret), encoder.encode(payload));
  return `${payload}.${toBase64Url(new Uint8Array(signature))}`;
}

export async function verifyDeletionCredential(
  secret: string,
  credential: string,
  operationKey: string,
  nowEpochSeconds: number,
): Promise<{ accountId: string; expired: boolean } | null> {
  const [payload, signature, extra] = credential.split(".");
  if (!payload || !signature || extra) return null;
  const valid = await crypto.subtle.verify(
    "HMAC", await signingKey(secret), fromBase64Url(signature), encoder.encode(payload),
  ).catch(() => false);
  if (!valid) return null;
  const decoded = JSON.parse(new TextDecoder().decode(fromBase64Url(payload))) as Record<string, unknown>;
  if (decoded.v !== 1 || decoded.operationKey !== operationKey || typeof decoded.accountId !== "string") return null;
  if (typeof decoded.exp !== "number") return null;
  return { accountId: decoded.accountId, expired: decoded.exp < nowEpochSeconds };
}
