import { validateInitialCopy } from "./service.ts";

const emptyCopy = {
  accountId: "10000000-0000-0000-0000-000000000001",
  aquariums: [],
  parameterDefinitions: [],
  sessions: [],
  measurements: [],
  maintenanceActions: [],
  events: [],
};

Deno.test("validateInitialCopy accepts the complete migration envelope unchanged", () => {
  if (validateInitialCopy(emptyCopy) !== emptyCopy) {
    throw new Error("the validated payload must retain its local entities and UUIDs");
  }
});

Deno.test("validateInitialCopy rejects an incomplete migration envelope", () => {
  const { events: _events, ...incomplete } = emptyCopy;
  try {
    validateInitialCopy(incomplete);
  } catch (error) {
    if (error instanceof TypeError && error.message === "Initial copy requires events") return;
    throw error;
  }
  throw new Error("an incomplete payload was accepted");
});
