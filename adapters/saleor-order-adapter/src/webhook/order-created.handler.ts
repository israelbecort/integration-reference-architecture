import type {
  FastifyReply,
  FastifyRequest,
} from "fastify";

import {
  OrderApiHttpError,
  OrderApiProtocolError,
  OrderApiTransportError,
  type OrderApiCreateOrder,
} from "../client/order-api.client.js";

import { env } from "../config/env.js";

import {
  createSaleorOrderIdempotencyKey,
} from "../idempotency/saleor-order-idempotency.js";

import {
  mapSaleorOrderToOrderApiRequest,
  SaleorOrderMappingError,
} from "../mapper/saleor-order.mapper.js";

import type {
  SaleorSignatureVerifier,
} from "../security/saleor-signature.js";

import {
  createCorrelationId,
} from "../tracing/correlation-id.js";

import {
  saleorOrderCreatedSchema,
} from "./order-created.schema.js";

type OrderCreatedHandlerDependencies = {
  verifySignature: SaleorSignatureVerifier;
  createOrder: OrderApiCreateOrder;
};

export function createOrderCreatedHandler(
  dependencies: OrderCreatedHandlerDependencies,
) {
  return async function orderCreatedHandler(
    request: FastifyRequest<{ Body: unknown }>,
    reply: FastifyReply,
  ): Promise<void> {
    const saleorEvent =
      request.headers["saleor-event"];

    const saleorDomain =
      request.headers["saleor-domain"];

    const saleorSignature =
      request.headers["saleor-signature"];

    request.log.info(
      {
        saleorEvent,
        saleorDomain,
        signaturePresent:
          typeof saleorSignature === "string" &&
          saleorSignature.length > 0,
      },
      "Saleor webhook received",
    );

    if (
      typeof saleorSignature !== "string" ||
      saleorSignature.length === 0
    ) {
      request.log.warn(
        "Saleor webhook signature is missing",
      );

      await reply.code(401).send({
        code: "SALEOR_SIGNATURE_MISSING",
        message:
          "Saleor webhook signature is required",
      });

      return;
    }

    const rawBody = request.rawBody;

    if (
      typeof rawBody !== "string" &&
      !Buffer.isBuffer(rawBody)
    ) {
      request.log.error(
        "Raw Saleor webhook body is unavailable",
      );

      await reply.code(500).send({
        code: "SALEOR_RAW_BODY_UNAVAILABLE",
        message:
          "Unable to verify Saleor webhook",
      });

      return;
    }

    try {
      await dependencies.verifySignature(
        rawBody,
        saleorSignature,
      );
    } catch (error) {
      request.log.warn(
        {
          err: error,
        },
        "Invalid Saleor webhook signature",
      );

      await reply.code(401).send({
        code: "SALEOR_INVALID_SIGNATURE",
        message:
          "Invalid Saleor webhook signature",
      });

      return;
    }

    request.log.info(
      "Saleor webhook signature verified",
    );

    const result =
      saleorOrderCreatedSchema.safeParse(
        request.body,
      );

    if (!result.success) {
      request.log.warn(
        {
          validationErrors:
            result.error.issues,
        },
        "Invalid Saleor ORDER_CREATED payload",
      );

      await reply.code(400).send({
        code: "SALEOR_INVALID_PAYLOAD",
        message:
          "Invalid Saleor ORDER_CREATED payload",
      });

      return;
    }

    const event = result.data;

    request.log.info(
      {
        saleorOrderId:
          event.order.id,

        orderNumber:
          event.order.number,

        orderStatus:
          event.order.status,

        currency:
          event.order.total.gross.currency,

        totalAmount:
          event.order.total.gross.amount,

        lineCount:
          event.order.lines.length,
      },
      "Valid Saleor ORDER_CREATED event",
    );

    if (env.WEBHOOK_PAYLOAD_LOGGING) {
      request.log.info(
        {
          payload: event,
        },
        "Saleor webhook payload",
      );
    }

    let canonicalOrder:
      ReturnType<
        typeof mapSaleorOrderToOrderApiRequest
      >;

    try {
      canonicalOrder =
        mapSaleorOrderToOrderApiRequest(
          event,
        );
    } catch (error) {
      if (
        error instanceof
        SaleorOrderMappingError
      ) {
        request.log.warn(
          {
            err: error,
            saleorOrderId:
              event.order.id,
          },
          "Unable to map Saleor order to canonical order",
        );

        await reply.code(422).send({
          code:
            "SALEOR_ORDER_MAPPING_FAILED",

          message:
            "Saleor order cannot be mapped to the canonical order contract",
        });

        return;
      }

      throw error;
    }

    const idempotencyKey =
      createSaleorOrderIdempotencyKey(
        event.order.id,
      );

    const correlationId =
      createCorrelationId();

    request.log.info(
      {
        saleorOrderId:
          event.order.id,

        externalOrderId:
          canonicalOrder.externalOrderId,

        idempotencyKey,
        correlationId,
      },
      "Forwarding canonical order to Order API",
    );

    try {
      const acceptedOrder =
        await dependencies.createOrder(
          canonicalOrder,
          {
            idempotencyKey,
            correlationId,
          },
        );

      request.log.info(
        {
          orderId:
            acceptedOrder.orderId,

          externalOrderId:
            acceptedOrder.externalOrderId,

          orderStatus:
            acceptedOrder.status,

          correlationId:
            acceptedOrder.correlationId,

          idempotencyKey,
        },
        "Order API accepted Saleor order",
      );

      await reply.code(202).send({
        status: "ACCEPTED",
      });

      return;
    } catch (error) {
      if (
        error instanceof
        OrderApiTransportError
      ) {
        request.log.error(
          {
            err: error,
            correlationId,
            idempotencyKey,
          },
          "Order API is unreachable",
        );

        await reply.code(503).send({
          code:
            "ORDER_API_UNAVAILABLE",

          message:
            "Order API is temporarily unavailable",
        });

        return;
      }

      if (
        error instanceof
        OrderApiProtocolError
      ) {
        request.log.error(
          {
            err: error,
            correlationId,
            idempotencyKey,
          },
          "Order API returned an invalid response",
        );

        await reply.code(502).send({
          code:
            "ORDER_API_INVALID_RESPONSE",

          message:
            "Order API returned an invalid response",
        });

        return;
      }

      if (
        error instanceof
        OrderApiHttpError
      ) {
        request.log.warn(
          {
            err: error,
            downstreamStatus:
              error.status,

            downstreamErrorCode:
              error.problem?.errorCode,

            correlationId,
            idempotencyKey,
          },
          "Order API rejected Saleor order",
        );

        if (error.status === 409) {
          await reply.code(409).send({
            code:
              "ORDER_API_CONFLICT",

            message:
              "Order API reported an order conflict",
          });

          return;
        }

        if (error.status >= 500) {
          await reply.code(503).send({
            code:
              "ORDER_API_UNAVAILABLE",

            message:
              "Order API is temporarily unavailable",
          });

          return;
        }

        await reply.code(422).send({
          code:
            "ORDER_API_REJECTED_ORDER",

          message:
            "Order API rejected the canonical order",
        });

        return;
      }

      request.log.error(
        {
          err: error,
          correlationId,
          idempotencyKey,
        },
        "Unexpected error while processing Saleor order",
      );

      await reply.code(500).send({
        code:
          "SALEOR_ORDER_PROCESSING_FAILED",

        message:
          "Unexpected error while processing Saleor order",
      });
    }
  };
}