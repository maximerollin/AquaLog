import { createClient } from "jsr:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const anonymousKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.test("an account reaches only its own folder of the private photo bucket", async () => {
  const admin = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const suffix = crypto.randomUUID();
  const password = `Aq!${suffix}`;
  const created = [];
  const sessions = [];
  try {
    for (const name of ["a", "b"]) {
      const email = `photo-${name}-${suffix}@example.com`;
      const user = await admin.auth.admin.createUser({ email, password, email_confirm: true });
      if (user.error || !user.data.user) throw user.error ?? new Error("user was not created");
      created.push(user.data.user.id);
      const client = createClient(url, anonymousKey, { auth: { persistSession: false, autoRefreshToken: false } });
      const signedIn = await client.auth.signInWithPassword({ email, password });
      if (signedIn.error || !signedIn.data.session) throw signedIn.error ?? new Error("sign-in failed");
      sessions.push(signedIn.data.session.access_token);
    }
    const [accountA] = created;
    const [tokenA, tokenB] = sessions;
    const path = `aquarium-photos/${accountA}/${crypto.randomUUID()}.jpg`;
    const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);
    const send = (method: string, route: string, token: string, body?: Uint8Array) =>
      fetch(`${url}/storage/v1/object/${route}`, {
        method,
        headers: {
          apikey: anonymousKey,
          authorization: `Bearer ${token}`,
          "x-upsert": "true",
          ...(body ? { "content-type": "image/jpeg" } : {}),
        },
        body,
      });

    const upload = await send("POST", path, tokenA, bytes);
    const uploaded = await upload.text();
    if (upload.status !== 200) throw new Error(`owner upload returned ${upload.status}: ${uploaded}`);

    const download = await send("GET", `authenticated/${path}`, tokenA);
    if (download.status !== 200) throw new Error(`owner download returned ${download.status}`);
    const stored = new Uint8Array(await download.arrayBuffer());
    if (stored.length !== bytes.length || stored.some((byte, index) => byte !== bytes[index])) {
      throw new Error("owner did not get the uploaded bytes back");
    }

    const foreignRead = await send("GET", `authenticated/${path}`, tokenB);
    await foreignRead.body?.cancel();
    if (foreignRead.status !== 400 && foreignRead.status !== 404) {
      throw new Error(`other account read returned ${foreignRead.status}`);
    }
    const foreignWrite = await send("POST", path, tokenB, bytes);
    await foreignWrite.body?.cancel();
    if (foreignWrite.ok) throw new Error("other account could overwrite the object");
    const foreignDelete = await send("DELETE", path, tokenB);
    await foreignDelete.body?.cancel();
    const stillThere = await send("GET", `authenticated/${path}`, tokenA);
    await stillThere.body?.cancel();
    if (stillThere.status !== 200) throw new Error("other account deleted the object");
    const anonymousRead = await fetch(`${url}/storage/v1/object/public/${path}`);
    await anonymousRead.body?.cancel();
    if (anonymousRead.ok) throw new Error("the bucket is publicly readable");
  } finally {
    for (const id of created) {
      const listed = await admin.storage.from("aquarium-photos").list(id);
      if (listed.data?.length) {
        await admin.storage.from("aquarium-photos").remove(listed.data.map((o) => `${id}/${o.name}`));
      }
      await admin.auth.admin.deleteUser(id);
    }
  }
});
