import Fastify, {
  type FastifyInstance,
} from "fastify";

import rawBody from "fastify-raw-body";

import {
  verifySaleorWebhookSignature,
  type SaleorSignatureVerifier,
} from "./security/saleor-signature.js";

import {
  createOrderCreatedHandler,
} from "./webhook/order-created.handler.js";

type BuildAppOptions = {
  verifySaleorSignature?: SaleorSignatureVerifier;
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

  const orderCreatedHandler =
    createOrderCreatedHandler({
      verifySignature,
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