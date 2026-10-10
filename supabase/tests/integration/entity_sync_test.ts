import { createClient } from "jsr:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const anonymousKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.test("two devices share entity sync while another account remains isolated", async () => {
  const admin = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const suffix = crypto.randomUUID();
  const ownerEmail = `sync-owner-${suffix}@example.com`;
  const otherEmail = `sync-other-${suffix}@example.com`;
  const password = `Aq!${suffix}`;
  const owner = await admin.auth.admin.createUser({
    email: ownerEmail,
    password,
    email_confirm: true,
  });
  const other = await admin.auth.admin.createUser({
    email: otherEmail,
    password,
    email_confirm: true,
  });
  if (owner.error || !owner.data.user || other.error || !other.data.user) {
    throw owner.error ?? other.error ??
      new Error("isolated users were not created");
  }
  const ownerId = owner.data.user.id;
  const aquariumId = crypto.randomUUID();
  const parameterId = crypto.randomUUID();
  const sessionId = crypto.randomUUID();
  const measurementId = crypto.randomUUID();
  const actionId = crypto.randomUUID();
  const eventId = crypto.randomUUID();

  try {
    const firstDevice = createClient(url, anonymousKey, {
      auth: { persistSession: false },
    });
    const secondDevice = createClient(url, anonymousKey, {
      auth: { persistSession: false },
    });
    const otherAccount = createClient(url, anonymousKey, {
      auth: { persistSession: false },
    });
    for (const client of [firstDevice, secondDevice]) {
      const signedIn = await client.auth.signInWithPassword({
        email: ownerEmail,
        password,
      });
      if (signedIn.error) throw signedIn.error;
    }
    const otherSignedIn = await otherAccount.auth.signInWithPassword({
      email: otherEmail,
      password,
    });
    if (otherSignedIn.error) throw otherSignedIn.error;

    const writes = [
      firstDevice.from("aquariums").upsert({
        id: aquariumId,
        account_id: ownerId,
        name: "Amazonien",
        volume: 120,
        volume_unit: "liters",
        created_at_epoch_millis: 1700000000000,
        profile: "established",
      }),
      firstDevice.from("parameter_definitions").upsert({
        id: parameterId,
        account_id: ownerId,
        aquarium_id: aquariumId,
        parameter: "temperature",
        is_active: true,
        position: 0,
        unit: "°C",
        precision: 1,
        indicative_minimum: 23,
        indicative_maximum: 27,
      }),
      firstDevice.from("sessions").upsert({
        id: sessionId,
        account_id: ownerId,
        aquarium_id: aquariumId,
        occurred_at_epoch_millis: 1700000100000,
        created_at_epoch_millis: 1700000101000,
      }),
      firstDevice.from("measurements").upsert({
        id: measurementId,
        account_id: ownerId,
        session_id: sessionId,
        parameter_definition_id: parameterId,
        value: 24.5,
      }),
      firstDevice.from("maintenance_actions").upsert({
        id: actionId,
        account_id: ownerId,
        session_id: sessionId,
        type: "water_change",
        quantity: 20,
        unit: "%",
        product: null,
      }),
      firstDevice.from("session_events").upsert({
        id: eventId,
        account_id: ownerId,
        session_id: sessionId,
        type: "observation",
        note: "Eau claire",
      }),
    ];
    for (const write of writes) {
      const result = await write;
      if (result.error) throw result.error;
    }

    for (
      const table of [
        "aquariums",
        "parameter_definitions",
        "sessions",
        "measurements",
        "maintenance_actions",
        "session_events",
      ]
    ) {
      const visible = await secondDevice.from(table).select("id");
      if (visible.error) throw visible.error;
      if (visible.data.length !== 1) {
        throw new Error(`second device did not download ${table}`);
      }
      const isolated = await otherAccount.from(table).select("id");
      if (isolated.error) throw isolated.error;
      if (isolated.data.length !== 0) {
        throw new Error(`other account could read ${table}`);
      }
    }

    const aquariumUpdate = await firstDevice.from("aquariums")
      .update({ name: "Amazonien planté", volume: 180 })
      .eq("id", aquariumId);
    if (aquariumUpdate.error) throw aquariumUpdate.error;
    const parameterUpdate = await firstDevice.from("parameter_definitions")
      .update({ unit: "C", precision: 2 })
      .eq("id", parameterId);
    if (parameterUpdate.error) throw parameterUpdate.error;
    for (const table of ["measurements", "maintenance_actions", "session_events"]) {
      const removed = await firstDevice.from(table).delete().eq("account_id", ownerId);
      if (removed.error) throw removed.error;
      const downloaded = await secondDevice.from(table).select("id");
      if (downloaded.error) throw downloaded.error;
      if (downloaded.data.length !== 0) throw new Error(`second device retained deleted ${table}`);
    }
    const updatedAquarium = await secondDevice.from("aquariums").select("name,volume").single();
    if (updatedAquarium.error || updatedAquarium.data.name !== "Amazonien planté" || updatedAquarium.data.volume !== 180) {
      throw updatedAquarium.error ?? new Error("second device did not observe Aquarium update");
    }
    const updatedParameter = await secondDevice.from("parameter_definitions").select("unit,precision").single();
    if (updatedParameter.error || updatedParameter.data.unit !== "C" || updatedParameter.data.precision !== 2) {
      throw updatedParameter.error ?? new Error("second device did not observe Parameter update");
    }
  } finally {
    for (
      const table of [
        "session_events",
        "maintenance_actions",
        "measurements",
        "sessions",
        "parameter_definitions",
        "aquariums",
      ]
    ) {
      await admin.from(table).delete().eq("account_id", ownerId);
    }
    await admin.auth.admin.deleteUser(ownerId);
    await admin.auth.admin.deleteUser(other.data.user.id);
  }
});
