import {
  describe,
  expect,
  it,
} from "vitest";

import {
  validate as uuidValidate,
  version as uuidVersion,
} from "uuid";

import {
  createCorrelationId,
} from "../src/tracing/correlation-id.js";

describe("Correlation ID", () => {
  it("generates a valid UUID version 4", () => {
    const correlationId =
      createCorrelationId();

    expect(
      uuidValidate(correlationId),
    ).toBe(true);

    expect(
      uuidVersion(correlationId),
    ).toBe(4);
  });

  it("generates a new value for each processing attempt", () => {
    const first =
      createCorrelationId();

    const second =
      createCorrelationId();

    expect(first).not.toBe(second);
  });
});
