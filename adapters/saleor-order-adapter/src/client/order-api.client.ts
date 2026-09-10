import { z } from "zod";

import { env } from "../config/env.js";

import type {
  CreateOrderRequest,
  OrderAcceptedResponse,
  OrderApiProblemDetails,
  OrderApiRequestContext,
} from "./order-api.types.js";

const orderAcceptedResponseSchema = z
  .object({
    orderId: z.string().uuid(),
    externalOrderId: z.string().min(1),
    status: z.literal("PROCESSING"),
    correlationId: z.string().uuid(),
    acceptedAt: z.string().datetime({
      offset: true,
    }),
  })
  .strict();

const problemDetailsSchema = z
  .object({
    type: z.string(),
    title: z.string(),
    status: z.number().int().min(400).max(599),
    detail: z.string(),
    instance: z.string(),
    errorCode: z.string(),
    correlationId: z.string().uuid(),
    timestamp: z.string().datetime({
      offset: true,
    }),
  })
  .strict();

export type OrderApiCreateOrder = (
  request: CreateOrderRequest,
  context: OrderApiRequestContext,
) => Promise<OrderAcceptedResponse>;

export class OrderApiHttpError extends Error {
  readonly status: number;
  readonly problem:
    | OrderApiProblemDetails
    | undefined;

  constructor(
    status: number,
    problem?: OrderApiProblemDetails,
  ) {
    super(
      problem
        ? `Order API returned HTTP ${status} (${problem.errorCode})`
        : `Order API returned HTTP ${status}`,
    );

    this.name = "OrderApiHttpError";
    this.status = status;
    this.problem = problem;
  }
}

export class OrderApiTransportError extends Error {
  constructor(
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = "OrderApiTransportError";
  }
}

export class OrderApiProtocolError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "OrderApiProtocolError";
  }
}

type OrderApiClientOptions = {
  baseUrl?: string;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
};

export function createOrderApiClient(
  options: OrderApiClientOptions = {},
): OrderApiCreateOrder {
  const baseUrl =
    options.baseUrl ??
    env.ORDER_API_BASE_URL;

  const fetchImpl =
    options.fetchImpl ??
    fetch;

  const timeoutMs =
    options.timeoutMs ??
    5_000;

  return async function createOrder(
    request: CreateOrderRequest,
    context: OrderApiRequestContext,
  ): Promise<OrderAcceptedResponse> {
    const url = new URL(
      "/api/v1/orders",
      baseUrl,
    );

    let response: Response;

    try {
      response = await fetchImpl(
        url,
        {
          method: "POST",

          headers: {
            "content-type":
              "application/json",

            accept:
              "application/json, application/problem+json",

            "idempotency-key":
              context.idempotencyKey,

            "x-correlation-id":
              context.correlationId,
          },

          body: JSON.stringify(request),

          signal:
            AbortSignal.timeout(
              timeoutMs,
            ),
        },
      );
    } catch (error) {
      throw new OrderApiTransportError(
        "Unable to reach Order API",
        {
          cause: error,
        },
      );
    }

    const responseText =
      await response.text();

    let responseBody: unknown;

    if (responseText.length > 0) {
      try {
        responseBody =
          JSON.parse(responseText);
      } catch {
        responseBody = undefined;
      }
    }

    if (response.status !== 202) {
      const problemResult =
        problemDetailsSchema.safeParse(
          responseBody,
        );

      throw new OrderApiHttpError(
        response.status,
        problemResult.success
          ? problemResult.data
          : undefined,
      );
    }

    const acceptedResult =
      orderAcceptedResponseSchema.safeParse(
        responseBody,
      );

    if (!acceptedResult.success) {
      throw new OrderApiProtocolError(
        "Order API returned an invalid 202 response",
      );
    }

    const accepted =
      acceptedResult.data;

    if (
      accepted.externalOrderId !==
      request.externalOrderId
    ) {
      throw new OrderApiProtocolError(
        "Order API response contains an unexpected externalOrderId",
      );
    }

    if (
      accepted.correlationId !==
      context.correlationId
    ) {
      throw new OrderApiProtocolError(
        "Order API response contains an unexpected correlationId",
      );
    }

    const responseCorrelationId =
      response.headers.get(
        "x-correlation-id",
      );

    if (
      responseCorrelationId !==
      context.correlationId
    ) {
      throw new OrderApiProtocolError(
        "Order API response contains an unexpected X-Correlation-Id header",
      );
    }

    return accepted;
  };
}
