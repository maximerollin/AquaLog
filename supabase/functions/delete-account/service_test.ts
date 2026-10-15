import {
  AccountDeletionAdmin,
  AccountFileStorage,
  deleteAccount,
  deleteAccountFiles,
  issueDeletionCredential,
  verifyDeletionCredential,
} from "./service.ts";

class FakeAdmin implements AccountDeletionAdmin {
  calls: string[] = [];
  completed = new Set<string>();
  existingUsers = new Set(["account-a", "account-b"]);
  failAt: string | null = null;
  isCompleted(accountId: string, operationKey: string) {
    this.calls.push(`receipt:${accountId}:${operationKey}`);
    return Promise.resolve(this.completed.has(`${accountId}:${operationKey}`));
  }
  authUserExists(accountId: string) { this.calls.push(`auth-exists:${accountId}`); return Promise.resolve(this.existingUsers.has(accountId)); }
  deleteFiles(accountId: string) { this.calls.push(`files:${accountId}`); return this.result("files"); }
  deleteDatabaseData(_token: string, operationKey: string) { this.calls.push(`database:${operationKey}`); return this.result("database"); }
  deleteAuthUser(accountId: string) { this.calls.push(`auth:${accountId}`); return this.result("auth"); }
  private result(step: string): Promise<void> { return this.failAt === step ? Promise.reject(new Error(step)) : Promise.resolve(); }
}

Deno.test("receipt lookup is scoped to the already authenticated account", async () => {
  const admin = new FakeAdmin();
  admin.completed.add("account-a:shared-key");
  const result = await deleteAccount(admin, "account-b", "account-b", "token-b", "shared-key");
  if (result.status !== "completed") throw new Error(`unexpected ${result.status}`);
  if (admin.calls[0] !== "receipt:account-b:shared-key") throw new Error("receipt was not owner scoped");
  if (admin.calls.includes("auth:account-a")) throw new Error("B could affect A");
});

Deno.test("expired Auth can only finish with its signed account-bound credential", async () => {
  const credential = await issueDeletionCredential("secret", "account-a", "operation-a", 200);
  if ((await verifyDeletionCredential("secret", credential, "operation-a", 100))?.accountId !== "account-a") throw new Error("valid credential rejected");
  if (await verifyDeletionCredential("secret", credential, "operation-b", 100) !== null) throw new Error("credential crossed operation");
  if (await verifyDeletionCredential("other", credential, "operation-a", 100) !== null) throw new Error("forged credential accepted");
  const admin = new FakeAdmin();
  admin.completed.add("account-a:operation-a");
  admin.existingUsers.delete("account-a");
  const claims = await verifyDeletionCredential("secret", credential, "operation-a", 300);
  if (!claims?.expired) throw new Error("expiry state was lost");
  const result = await deleteAccount(admin, claims.accountId, null, "expired", "operation-a");
  if (result.status !== "completed") throw new Error("safe recovery failed");
});

Deno.test("unknown operation with expired Auth is truthful and non destructive", async () => {
  const admin = new FakeAdmin();
  const result = await deleteAccount(admin, "account-a", null, "expired", "unknown");
  if (result.status !== "authentication_required") throw new Error("unknown operation reported complete");
  if (admin.calls.some((call) => call.startsWith("files:") || call.startsWith("database:") || call.startsWith("auth:"))) {
    throw new Error("unknown operation changed state");
  }
});

Deno.test("deletion removes files and database before Auth", async () => {
  const admin = new FakeAdmin();
  const result = await deleteAccount(admin, "account-a", "account-a", "access", "operation-1");
  if (result.status !== "completed") throw new Error(`unexpected ${result.status}`);
  const expected = "receipt:account-a:operation-1,files:account-a,database:operation-1,auth:account-a";
  if (admin.calls.join() !== expected) throw new Error(admin.calls.join());
});

Deno.test("a completed database receipt redrains recreated files before retrying Auth deletion", async () => {
  const admin = new FakeAdmin();
  admin.completed.add("account-a:operation-1");

  const result = await deleteAccount(admin, "account-a", null, "expired", "operation-1");

  if (result.status !== "completed") throw new Error(`unexpected ${result.status}`);
  const expected = "receipt:account-a:operation-1,files:account-a,auth-exists:account-a,auth:account-a";
  if (admin.calls.join() !== expected) throw new Error(admin.calls.join());
});

class FakeStorage implements AccountFileStorage {
  objects = new Set<string>();
  failRemovalOnce = false;
  list(_bucket: string, prefix: string, limit: number) {
    const descendants = [...this.objects].filter((path) => path.startsWith(`${prefix}/`)).map((path) => path.slice(prefix.length + 1));
    const names = new Map<string, string | null>();
    for (const remainder of descendants) {
      const [name, ...nested] = remainder.split("/");
      names.set(name, nested.length === 0 ? `id-${name}` : null);
    }
    return Promise.resolve([...names].slice(0, limit).map(([name, id]) => ({ name, id })));
  }
  remove(_bucket: string, paths: string[]) {
    if (this.failRemovalOnce) { this.failRemovalOnce = false; return Promise.reject(new Error("network")); }
    paths.forEach((path) => this.objects.delete(path));
    return Promise.resolve();
  }
}

Deno.test("storage drains page zero repeatedly and recursively removes more than 200 nested objects", async () => {
  const storage = new FakeStorage();
  for (let index = 0; index < 250; index += 1) storage.objects.add(`account-a/root-${index}`);
  for (let index = 0; index < 75; index += 1) storage.objects.add(`account-a/nested/deeper/object-${index}`);
  for (let index = 0; index < 125; index += 1) storage.objects.add(`account-a/folder-${index}/object`);
  await deleteAccountFiles(storage, ["exports"], "account-a");
  if (storage.objects.size !== 0) throw new Error(`${storage.objects.size} objects were skipped`);
});

Deno.test("partial storage failure is replay safe", async () => {
  const storage = new FakeStorage();
  storage.objects.add("account-a/one");
  storage.objects.add("account-a/nested/two");
  storage.failRemovalOnce = true;
  try { await deleteAccountFiles(storage, ["exports"], "account-a"); } catch { /* expected */ }
  await deleteAccountFiles(storage, ["exports"], "account-a");
  if (storage.objects.size !== 0) throw new Error("replay left files behind");
});
