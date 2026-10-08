const requiredCollections = [
  "aquariums",
  "parameterDefinitions",
  "sessions",
  "measurements",
  "maintenanceActions",
  "events",
] as const;

export type InitialCopy = Record<(typeof requiredCollections)[number], unknown[]> & {
  accountId: string;
};

export function validateInitialCopy(value: unknown): InitialCopy {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError("Initial copy must be a JSON object");
  }
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.accountId !== "string" || candidate.accountId.length === 0) {
    throw new TypeError("Initial copy requires accountId");
  }
  for (const collection of requiredCollections) {
    if (!Array.isArray(candidate[collection])) {
      throw new TypeError(`Initial copy requires ${collection}`);
    }
  }
  return candidate as InitialCopy;
}
