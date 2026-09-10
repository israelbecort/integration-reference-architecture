import { buildApp } from "./app.js";
import { env } from "./config/env.js";

const app = await buildApp();

const shutdown = async (signal: string): Promise<void> => {
  app.log.info(
    {
      signal,
    },
    "Shutting down Saleor Order Adapter",
  );

  await app.close();
  process.exit(0);
};

process.on("SIGINT", () => {
  void shutdown("SIGINT");
});

process.on("SIGTERM", () => {
  void shutdown("SIGTERM");
});

try {
  await app.listen({
    port: env.PORT,
    host: env.HOST,
  });

  app.log.info(
    {
      port: env.PORT,
    },
    "Saleor Order Adapter started",
  );
} catch (error) {
  app.log.error(error);
  process.exit(1);
}