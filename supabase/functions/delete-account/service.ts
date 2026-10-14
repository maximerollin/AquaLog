export interface AccountDeletionAdmin {
  completedAccountId(operationKey: string): Promise<string | null>;
  authUserExists(accountId: string): Promise<boolean>;
  authenticatedAccountId(accessToken: string): Promise<string | null>;
  deleteFiles(accountId: string): Promise<void>;
  deleteDatabaseData(accessToken: string, operationKey: string): Promise<void>;
  deleteAuthUser(accountId: string): Promise<void>;
}

export type AccountDeletionOutcome =
  | { status: "completed"; accountId: string }
  | { status: "authentication_required" }
  | { status: "retryable_failure"; step: "files" | "database" | "auth" };

export async function deleteAccount(
  admin: AccountDeletionAdmin,
  accessToken: string,
  operationKey: string,
): Promise<AccountDeletionOutcome> {
  if (!operationKey.trim() || operationKey.length > 200) {
    throw new TypeError("A valid deletion operation key is required");
  }
  const completed = await admin.completedAccountId(operationKey);
  if (completed) {
    if (!await admin.authUserExists(completed)) return { status: "completed", accountId: completed };
    try {
      await admin.deleteAuthUser(completed);
      return { status: "completed", accountId: completed };
    } catch {
      return { status: "retryable_failure", step: "auth" };
    }
  }
  const accountId = await admin.authenticatedAccountId(accessToken);
  if (!accountId) return { status: "authentication_required" };
  try {
    await admin.deleteFiles(accountId);
  } catch {
    return { status: "retryable_failure", step: "files" };
  }
  try {
    await admin.deleteDatabaseData(accessToken, operationKey);
  } catch {
    return { status: "retryable_failure", step: "database" };
  }
  try {
    await admin.deleteAuthUser(accountId);
  } catch {
    return { status: "retryable_failure", step: "auth" };
  }
  return { status: "completed", accountId };
}

