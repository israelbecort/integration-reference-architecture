import type { FastifyInstance } from "fastify";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

import { buildApp } from "../src/app.js";

const validOrderCreatedPayload = {
  __typename: "OrderCreated",
  issuedAt: "2026-09-09T14:05:04.071532+00:00",
  version: "3.23.31",
  order: {
    id: "T3JkZXI6ODEyZWQ3MDEtMTZjOC00NjcyLTg3MGYtMjMxMDcxODIxNDM5",
    number: "25",
    created: "2026-09-09T14:05:02.478473+00:00",
    status: "UNFULFILLED",
    userEmail: "integration.test@example.com",

    total: {
      gross: {
        amount: 35,
        currency: "USD",
      },
    },

    shippingAddress: {
      firstName: "Test",
      lastName: "Customer",
      companyName: "",
      streetAddress1: "1 Market St",
      streetAddress2: "",
      city: "SAN FRANCISCO",
      postalCode: "94105",
      countryArea: "CA",
      country: {
        code: "US",
        country: "United States of America",
      },
      phone: "",
    },

    billingAddress: {
      firstName: "Test",
      lastName: "Customer",
      companyName: "",
      streetAddress1: "1 Market St",
      streetAddress2: "",
      city: "SAN FRANCISCO",
      postalCode: "94105",
      countryArea: "CA",
      country: {
        code: "US",
        country: "United States of America",
      },
      phone: "",
    },

    lines: [
      {
        id: "T3JkZXJMaW5lOjU4OTk3NDQwLTgzNjctNGJjYi05MmIwLWUyYWQ1MWZlNTgwZA==",
        quantity: 1,
        productName: "Blue Hoodie",
        variantName: "UHJvZHVjdFZhcmlhbnQ6MzQ2",
        productSku: null,
        unitPrice: {
          gross: {
            amount: 35,
            currency: "USD",
          },
        },
        totalPrice: {
          gross: {
            amount: 35,
            currency: "USD",
          },
        },
      },
    ],
  },
};

describe("POST /webhooks/saleor/order-created", () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    app = await buildApp({
      verifySaleorSignature:
        vi.fn().mockResolvedValue(undefined),
    });
  
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("accepts a valid Saleor ORDER_CREATED event", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/webhooks/saleor/order-created",
      headers: {
        "content-type": "application/json",
        "saleor-event": "order_created",
        "saleor-domain": "localhost:8000",
        "saleor-signature": "test-signature",
      },
      payload: validOrderCreatedPayload,
    });

    expect(response.statusCode).toBe(202);
    expect(JSON.parse(response.body)).toEqual({
      status: "ACCEPTED",
    });
  });

  it("accepts a Saleor order without a product SKU", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/webhooks/saleor/order-created",
      headers: {
        "content-type": "application/json",
        "saleor-event": "order_created",
        "saleor-domain": "localhost:8000",
        "saleor-signature": "test-signature",
      },
      payload: validOrderCreatedPayload,
    });

    expect(response.statusCode).toBe(202);
  });

  it("rejects a payload that is not an OrderCreated event", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/webhooks/saleor/order-created",
      headers: {
        "content-type": "application/json",
        "saleor-event": "order_created",
        "saleor-domain": "localhost:8000",
        "saleor-signature": "test-signature",
      },
      payload: {
        ...validOrderCreatedPayload,
        __typename: "OrderUpdated",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(JSON.parse(response.body)).toEqual({
      code: "SALEOR_INVALID_PAYLOAD",
      message: "Invalid Saleor ORDER_CREATED payload",
    });
  });

  it("rejects a webhook without a Saleor signature", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/webhooks/saleor/order-created",
      headers: {
        "content-type": "application/json",
        "saleor-event": "order_created",
        "saleor-domain": "localhost:8000",
      },
      payload: validOrderCreatedPayload,
    });
  
    expect(response.statusCode).toBe(401);
  
    expect(JSON.parse(response.body)).toEqual({
      code: "SALEOR_SIGNATURE_MISSING",
      message:
        "Saleor webhook signature is required",
    });
  });

  it("rejects a webhook with an invalid Saleor signature", async () => {
    const appWithInvalidSignature =
      await buildApp({
        verifySaleorSignature:
          vi.fn().mockRejectedValue(
            new Error("Invalid signature"),
          ),
      });
  
    await appWithInvalidSignature.ready();
  
    try {
      const response =
        await appWithInvalidSignature.inject({
          method: "POST",
          url: "/webhooks/saleor/order-created",
          headers: {
            "content-type": "application/json",
            "saleor-event": "order_created",
            "saleor-domain": "localhost:8000",
            "saleor-signature":
              "invalid-signature",
          },
          payload: validOrderCreatedPayload,
        });
  
      expect(response.statusCode).toBe(401);
  
      expect(
        JSON.parse(response.body),
      ).toEqual({
        code: "SALEOR_INVALID_SIGNATURE",
        message:
          "Invalid Saleor webhook signature",
      });
    } finally {
      await appWithInvalidSignature.close();
    }
  });
});
