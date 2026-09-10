import { loadEnvFile } from "node:process";
import { z } from "zod";

try {
  loadEnvFile();
} catch (error) {
  const nodeError = error as NodeJS.ErrnoException;

  if (nodeError.code !== "ENOENT") {
    throw error;
  }
}

const envSchema = z.object({
  PORT: z.coerce.number().int().min(1).max(65535).default(8082),

  HOST: z.string().default("0.0.0.0"),

  SALEOR_BASE_URL: z
    .string()
    .url()
    .default("http://localhost:8000"),

  ORDER_API_BASE_URL: z
    .string()
    .url()
    .default("http://localhost:8080"),

  WEBHOOK_PAYLOAD_LOGGING: z
    .enum(["true", "false"])
    .default("false")
    .transform((value) => value === "true"),
});

const result = envSchema.safeParse(process.env);

if (!result.success) {
  throw new Error(
    `Invalid environment configuration: ${result.error.message}`,
  );
}

export const env = result.data;