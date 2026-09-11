import Fastify, {
  type FastifyInstance,
} from "fastify";

import rawBody from "fastify-raw-body";

import {
  createOrderApiClient,
  type OrderApiCreateOrder,
} from "./client/order-api.client.js";

import {
  verifySaleorWebhookSignature,
  type SaleorSignatureVerifier,
} from "./security/saleor-signature.js";

import {
  createOrderCreatedHandler,
} from "./webhook/order-created.handler.js";

type BuildAppOptions = {
  verifySaleorSignature?: SaleorSignatureVerifier;
  createOrder?: OrderApiCreateOrder;
};

export async function buildApp(
  options: BuildAppOptions = {},
): Promise<FastifyInstance> {
  const app = Fastify({
    logger: true,
  });

  await app.register(rawBody, {
    field: "rawBody",
    global: false,
    encoding: "utf8",
    runFirst: true,
  });

  const verifySignature =
    options.verifySaleorSignature ??
    verifySaleorWebhookSignature;

  const createOrder =
    options.createOrder ??
    createOrderApiClient();

  const orderCreatedHandler =
    createOrderCreatedHandler({
      verifySignature,
      createOrder,
    });

  app.get(
    "/health",
    async () => ({
      status: "UP",
    }),
  );

  app.post(
    "/webhooks/saleor/order-created",
    {
      config: {
        rawBody: true,
      },
    },
    orderCreatedHandler,
  );

  return app;
}