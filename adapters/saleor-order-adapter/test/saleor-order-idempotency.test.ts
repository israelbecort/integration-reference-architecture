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
  createSaleorOrderIdempotencyKey,
} from "../src/idempotency/saleor-order-idempotency.js";

describe("Saleor order idempotency key", () => {
  it("generates the same key for the same Saleor order", () => {
    const saleorOrderId =
      "T3JkZXI6Yzg2MWFmNmItYzE0OC00ZTYwLWIxNTYtNjBkMGY2NDQxYzA5";

    const first =
      createSaleorOrderIdempotencyKey(
        saleorOrderId,
      );

    const second =
      createSaleorOrderIdempotencyKey(
        saleorOrderId,
      );

    expect(first).toBe(second);
  });

  it("generates different keys for different Saleor orders", () => {
    const first =
      createSaleorOrderIdempotencyKey(
        "saleor-order-1",
      );

    const second =
      createSaleorOrderIdempotencyKey(
        "saleor-order-2",
      );

    expect(first).not.toBe(second);
  });

  it("generates a UUID version 5", () => {
    const key =
      createSaleorOrderIdempotencyKey(
        "saleor-order-1",
      );

    expect(uuidValidate(key)).toBe(true);
    expect(uuidVersion(key)).toBe(5);
  });

  it("keeps the expected key for the real Saleor order fixture", () => {
    const key =
      createSaleorOrderIdempotencyKey(
        "T3JkZXI6Yzg2MWFmNmItYzE0OC00ZTYwLWIxNTYtNjBkMGY2NDQxYzA5",
      );

    expect(key).toBe(
      "f565a672-b374-5bbb-aa7b-d15f867513e0",
    );
  });
});
