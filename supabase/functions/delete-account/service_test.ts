import { AccountDeletionAdmin, deleteAccount } from "./service.ts";

class FakeAdmin implements AccountDeletionAdmin {
  calls: string[] = [];
  completed: string | null = null;
  authenticated: string | null = "account-1";
  authExists = true;
  failAt: string | null = null;
  completedAccountId(operationKey: string) { this.calls.push(`receipt:${operationKey}`); return Promise.resolve(this.completed); }
  authUserExists(accountId: string) { this.calls.push(`auth-exists:${accountId}`); return Promise.resolve(this.authExists); }
  authenticatedAccountId(_token: string) { this.calls.push("authenticate"); return Promise.resolve(this.authenticated); }
  deleteFiles(accountId: string) { this.calls.push(`files:${accountId}`); return this.result("files"); }
  deleteDatabaseData(_token: string, operationKey: string) { this.calls.push(`database:${operationKey}`); return this.result("database"); }
  deleteAuthUser(accountId: string) { this.calls.push(`auth:${accountId}`); return this.result("auth"); }
  private result(step: string): Promise<void> { return this.failAt === step ? Promise.reject(new Error(step)) : Promise.resolve(); }
}

Deno.test("deletion removes files and database before the server-only Auth user deletion", async () => {
  const admin = new FakeAdmin();
  const result = await deleteAccount(admin, "access", "operation-1");
  if (result.status !== "completed") throw new Error(`unexpected ${result.status}`);
  const expected = "receipt:operation-1,authenticate,files:account-1,database:operation-1,auth:account-1";
  if (admin.calls.join() !== expected) throw new Error(admin.calls.join());
});

Deno.test("a partial file failure is retryable and never deletes database or Auth", async () => {
  const admin = new FakeAdmin();
  admin.failAt = "files";
  const result = await deleteAccount(admin, "access", "operation-1");
  if (result.status !== "retryable_failure" || result.step !== "files") throw new Error("failure was hidden");
  if (admin.calls.some((call) => call.startsWith("database:") || call.startsWith("auth:"))) throw new Error("later destructive step ran");
});

Deno.test("a persisted receipt makes retry safe after the Auth user has disappeared", async () => {
  const admin = new FakeAdmin();
  admin.completed = "account-1";
  admin.authenticated = null;
  admin.authExists = false;
  const result = await deleteAccount(admin, "expired", "operation-1");
  if (result.status !== "completed") throw new Error("completed deletion was not recovered");
  if (admin.calls.join() !== "receipt:operation-1,auth-exists:account-1") throw new Error("destructive work repeated");
});

Deno.test("a database receipt does not hide an Auth deletion that is still incomplete", async () => {
  const admin = new FakeAdmin();
  admin.completed = "account-1";
  admin.failAt = "auth";
  const result = await deleteAccount(admin, "access", "operation-1");
  if (result.status !== "retryable_failure" || result.step !== "auth") {
    throw new Error("incomplete Auth deletion was reported complete");
  }
});

