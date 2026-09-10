import {
  describe,
  expect,
  it,
  vi,
} from "vitest";

import {
  createOrderApiClient,
  OrderApiHttpError,
  OrderApiProtocolError,
  OrderApiTransportError,
} from "../src/client/order-api.client.js";

import type {
  CreateOrderRequest,
  OrderApiRequestContext,
} from "../src/client/order-api.types.js";

const request: CreateOrderRequest = {
  externalOrderId:
    "T3JkZXI6Yzg2MWFmNmItYzE0OC00ZTYwLWIxNTYtNjBkMGY2NDQxYzA5",

  customer: {
    customerId: "VXNlcjox",
    email: "admin@example.com",
  },

  items: [
    {
      productId:
        "UHJvZHVjdFZhcmlhbnQ6MzQy",
      quantity: 1,
      unitPrice: 50,
    },
  ],

  currency: "USD",

  shippingAddress: {
    addressLine1:
      "813 Howard Street",
    addressLine2: "",
    city: "OSWEGO",
    postalCode: "13126",
    country: "US",
  },
};

const context: OrderApiRequestContext = {
  idempotencyKey:
    "f565a672-b374-5bbb-aa7b-d15f867513e0",

  correlationId:
    "11111111-1111-4111-8111-111111111111",
};

const acceptedResponse = {
  orderId:
    "b243423f-8047-49ea-b79f-50027400c022",

  externalOrderId:
    request.externalOrderId,

  status: "PROCESSING",

  correlationId:
    context.correlationId,

  acceptedAt:
    "2026-09-10T18:00:00Z",
};

describe("Order API client", () => {
  it("sends the canonical order with integration identity headers", async () => {
    const fetchImpl =
      vi.fn<typeof fetch>();

    fetchImpl.mockResolvedValue(
      new Response(
        JSON.stringify(
          acceptedResponse,
        ),
        {
          status: 202,
          headers: {
            "content-type":
              "application/json",

            "x-correlation-id":
              context.correlationId,
          },
        },
      ),
    );

    const createOrder =
      createOrderApiClient({
        baseUrl:
          "http://order-api.test",

        fetchImpl,
      });

    const result =
      await createOrder(
        request,
        context,
      );

    expect(result).toEqual(
      acceptedResponse,
    );

    expect(fetchImpl).toHaveBeenCalledTimes(
      1,
    );

    const [
      url,
      init,
    ] =
      fetchImpl.mock.calls[0]!;

    expect(url.toString()).toBe(
      "http://order-api.test/api/v1/orders",
    );

    expect(init?.method).toBe(
      "POST",
    );

    expect(init?.headers).toEqual({
      "content-type":
        "application/json",

      accept:
        "application/json, application/problem+json",

      "idempotency-key":
        context.idempotencyKey,

      "x-correlation-id":
        context.correlationId,
    });

    expect(
      JSON.parse(
        init?.body as string,
      ),
    ).toEqual(request);
  });

  it("throws a typed HTTP error when Order API returns 503", async () => {
    const fetchImpl =
      vi.fn<typeof fetch>();

    fetchImpl.mockResolvedValue(
      new Response(
        JSON.stringify({
          type:
            "https://example.com/problems/dependency-unavailable",

          title:
            "Dependency unavailable",

          status: 503,

          detail:
            "Integration Service is unavailable",

          instance:
            "/api/v1/orders",

          errorCode:
            "ORD-DEPENDENCY-001",

          correlationId:
            context.correlationId,

          timestamp:
            "2026-09-10T18:00:00Z",
        }),
        {
          status: 503,

          headers: {
            "content-type":
              "application/problem+json",

            "x-correlation-id":
              context.correlationId,
          },
        },
      ),
    );

    const createOrder =
      createOrderApiClient({
        baseUrl:
          "http://order-api.test",

        fetchImpl,
      });

    try {
      await createOrder(
        request,
        context,
      );

      throw new Error(
        "Expected OrderApiHttpError",
      );
    } catch (error) {
      expect(error).toBeInstanceOf(
        OrderApiHttpError,
      );

      const httpError =
        error as OrderApiHttpError;

      expect(httpError.status).toBe(
        503,
      );

      expect(
        httpError.problem?.errorCode,
      ).toBe(
        "ORD-DEPENDENCY-001",
      );
    }
  });

  it("throws a transport error when Order API cannot be reached", async () => {
    const fetchImpl =
      vi.fn<typeof fetch>();

    fetchImpl.mockRejectedValue(
      new Error(
        "ECONNREFUSED",
      ),
    );

    const createOrder =
      createOrderApiClient({
        baseUrl:
          "http://order-api.test",

        fetchImpl,
      });

    await expect(
      createOrder(
        request,
        context,
      ),
    ).rejects.toBeInstanceOf(
      OrderApiTransportError,
    );
  });

  it("rejects an invalid 202 response body", async () => {
    const fetchImpl =
      vi.fn<typeof fetch>();

    fetchImpl.mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "PROCESSING",
        }),
        {
          status: 202,

          headers: {
            "x-correlation-id":
              context.correlationId,
          },
        },
      ),
    );

    const createOrder =
      createOrderApiClient({
        baseUrl:
          "http://order-api.test",

        fetchImpl,
      });

    await expect(
      createOrder(
        request,
        context,
      ),
    ).rejects.toBeInstanceOf(
      OrderApiProtocolError,
    );
  });

  it("rejects a 202 response with a different correlation id", async () => {
    const fetchImpl =
      vi.fn<typeof fetch>();

    const unexpectedCorrelationId =
      "22222222-2222-4222-8222-222222222222";

    fetchImpl.mockResolvedValue(
      new Response(
        JSON.stringify({
          ...acceptedResponse,

          correlationId:
            unexpectedCorrelationId,
        }),
        {
          status: 202,

          headers: {
            "x-correlation-id":
              unexpectedCorrelationId,
          },
        },
      ),
    );

    const createOrder =
      createOrderApiClient({
        baseUrl:
          "http://order-api.test",

        fetchImpl,
      });

    await expect(
      createOrder(
        request,
        context,
      ),
    ).rejects.toBeInstanceOf(
      OrderApiProtocolError,
    );
  });
});
