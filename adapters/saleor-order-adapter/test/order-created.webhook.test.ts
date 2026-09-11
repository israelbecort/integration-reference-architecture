import type { FastifyInstance } from "fastify";

import {
  validate as uuidValidate,
  version as uuidVersion,
} from "uuid";

import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

import { buildApp } from "../src/app.js";

import {
  OrderApiHttpError,
  OrderApiProtocolError,
  OrderApiTransportError,
  type OrderApiCreateOrder,
} from "../src/client/order-api.client.js";

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

    user: {
      id: "VXNlcjp0ZXN0LXVzZXI=",
    },

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

        variant: {
          id: "UHJvZHVjdFZhcmlhbnQ6MzQ2",
        },

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

  let createOrderMock:
    ReturnType<typeof vi.fn<OrderApiCreateOrder>>;

  beforeEach(async () => {
    createOrderMock =
      vi.fn<OrderApiCreateOrder>();

    createOrderMock.mockImplementation(
      async (
        orderRequest,
        context,
      ) => ({
        orderId:
          "b243423f-8047-49ea-b79f-50027400c022",

        externalOrderId:
          orderRequest.externalOrderId,

        status: "PROCESSING",

        correlationId:
          context.correlationId,

        acceptedAt:
          "2026-09-10T18:00:00Z",
      }),
    );

    app = await buildApp({
      verifySaleorSignature:
        vi.fn().mockResolvedValue(
          undefined,
        ),

      createOrder:
        createOrderMock,
    });

    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("accepts a valid Saleor ORDER_CREATED event", async () => {
    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(202);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      status: "ACCEPTED",
    });

    expect(
      createOrderMock,
    ).toHaveBeenCalledTimes(1);

    const [
      canonicalOrder,
      context,
    ] =
      createOrderMock.mock.calls[0]!;

    expect(
      canonicalOrder,
    ).toEqual({
      externalOrderId:
        "T3JkZXI6ODEyZWQ3MDEtMTZjOC00NjcyLTg3MGYtMjMxMDcxODIxNDM5",

      customer: {
        customerId:
          "VXNlcjp0ZXN0LXVzZXI=",

        email:
          "integration.test@example.com",
      },

      items: [
        {
          productId:
            "UHJvZHVjdFZhcmlhbnQ6MzQ2",

          quantity: 1,
          unitPrice: 35,
        },
      ],

      currency: "USD",

      shippingAddress: {
        addressLine1:
          "1 Market St",

        addressLine2: "",

        city:
          "SAN FRANCISCO",

        postalCode:
          "94105",

        country: "US",
      },
    });

    expect(
      context.idempotencyKey,
    ).toBe(
      "4489b4e0-bae8-5377-aabc-81479167854a",
    );

    expect(
      uuidValidate(
        context.correlationId,
      ),
    ).toBe(true);

    expect(
      uuidVersion(
        context.correlationId,
      ),
    ).toBe(4);
  });

  it("accepts a Saleor order without a product SKU", async () => {
    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(202);

    expect(
      createOrderMock,
    ).toHaveBeenCalledTimes(1);
  });

  it("rejects a payload that is not an OrderCreated event", async () => {
    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload: {
          ...validOrderCreatedPayload,

          __typename:
            "OrderUpdated",
        },
      });

    expect(
      response.statusCode,
    ).toBe(400);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "SALEOR_INVALID_PAYLOAD",

      message:
        "Invalid Saleor ORDER_CREATED payload",
    });

    expect(
      createOrderMock,
    ).not.toHaveBeenCalled();
  });

  it("rejects a webhook without a Saleor signature", async () => {
    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(401);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "SALEOR_SIGNATURE_MISSING",

      message:
        "Saleor webhook signature is required",
    });

    expect(
      createOrderMock,
    ).not.toHaveBeenCalled();
  });

  it("rejects a webhook with an invalid Saleor signature", async () => {
    const appWithInvalidSignature =
      await buildApp({
        verifySaleorSignature:
          vi.fn().mockRejectedValue(
            new Error(
              "Invalid signature",
            ),
          ),

        createOrder:
          createOrderMock,
      });

    await appWithInvalidSignature.ready();

    try {
      const response =
        await appWithInvalidSignature.inject(
          {
            method: "POST",

            url:
              "/webhooks/saleor/order-created",

            headers: {
              "content-type":
                "application/json",

              "saleor-event":
                "order_created",

              "saleor-domain":
                "localhost:8000",

              "saleor-signature":
                "invalid-signature",
            },

            payload:
              validOrderCreatedPayload,
          },
        );

      expect(
        response.statusCode,
      ).toBe(401);

      expect(
        JSON.parse(response.body),
      ).toEqual({
        code:
          "SALEOR_INVALID_SIGNATURE",

        message:
          "Invalid Saleor webhook signature",
      });

      expect(
        createOrderMock,
      ).not.toHaveBeenCalled();
    } finally {
      await appWithInvalidSignature.close();
    }
  });

  it("accepts a guest Saleor order without a registered user", async () => {
    const guestPayload = {
      ...validOrderCreatedPayload,

      order: {
        ...validOrderCreatedPayload.order,

        user: null,
      },
    };

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          guestPayload,
      });

    expect(
      response.statusCode,
    ).toBe(202);

    expect(
      createOrderMock,
    ).toHaveBeenCalledTimes(1);

    const [
      canonicalOrder,
    ] =
      createOrderMock.mock.calls[0]!;

    expect(
      canonicalOrder.customer,
    ).toEqual({
      customerId: null,

      email:
        "integration.test@example.com",
    });
  });

  it("returns 422 when a Saleor order cannot be mapped", async () => {
    const payloadWithoutVariant = {
      ...validOrderCreatedPayload,

      order: {
        ...validOrderCreatedPayload.order,

        lines:
          validOrderCreatedPayload.order.lines.map(
            (line) => ({
              ...line,

              variant: null,
            }),
          ),
      },
    };

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          payloadWithoutVariant,
      });

    expect(
      response.statusCode,
    ).toBe(422);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "SALEOR_ORDER_MAPPING_FAILED",

      message:
        "Saleor order cannot be mapped to the canonical order contract",
    });

    expect(
      createOrderMock,
    ).not.toHaveBeenCalled();
  });

  it("returns 503 when Order API is unavailable", async () => {
    createOrderMock.mockRejectedValueOnce(
      new OrderApiTransportError(
        "Unable to reach Order API",
      ),
    );

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(503);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "ORDER_API_UNAVAILABLE",

      message:
        "Order API is temporarily unavailable",
    });
  });

  it("returns 503 when Order API returns a server error", async () => {
    createOrderMock.mockRejectedValueOnce(
      new OrderApiHttpError(
        503,
      ),
    );

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(503);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "ORDER_API_UNAVAILABLE",

      message:
        "Order API is temporarily unavailable",
    });
  });

  it("returns 409 when Order API reports a conflict", async () => {
    createOrderMock.mockRejectedValueOnce(
      new OrderApiHttpError(
        409,
      ),
    );

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(409);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "ORDER_API_CONFLICT",

      message:
        "Order API reported an order conflict",
    });
  });

  it("returns 502 when Order API violates the expected response contract", async () => {
    createOrderMock.mockRejectedValueOnce(
      new OrderApiProtocolError(
        "Invalid Order API response",
      ),
    );

    const response =
      await app.inject({
        method: "POST",

        url:
          "/webhooks/saleor/order-created",

        headers: {
          "content-type":
            "application/json",

          "saleor-event":
            "order_created",

          "saleor-domain":
            "localhost:8000",

          "saleor-signature":
            "test-signature",
        },

        payload:
          validOrderCreatedPayload,
      });

    expect(
      response.statusCode,
    ).toBe(502);

    expect(
      JSON.parse(response.body),
    ).toEqual({
      code:
        "ORDER_API_INVALID_RESPONSE",

      message:
        "Order API returned an invalid response",
    });
  });
});