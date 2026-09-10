import type {
  FastifyReply,
  FastifyRequest,
} from "fastify";

import { env } from "../config/env.js";
import type { SaleorSignatureVerifier } from "../security/saleor-signature.js";
import { saleorOrderCreatedSchema } from "./order-created.schema.js";

type OrderCreatedHandlerDependencies = {
  verifySignature: SaleorSignatureVerifier;
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
        message: "Saleor webhook signature is required",
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
        message: "Unable to verify Saleor webhook",
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
        message: "Invalid Saleor webhook signature",
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
          validationErrors: result.error.issues,
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
        saleorOrderId: event.order.id,
        orderNumber: event.order.number,
        orderStatus: event.order.status,
        currency:
          event.order.total.gross.currency,
        totalAmount:
          event.order.total.gross.amount,
        lineCount: event.order.lines.length,
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

    await reply.code(202).send({
      status: "ACCEPTED",
    });
  };
}