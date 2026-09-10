import { createRemoteJWKSet } from "jose";

import { env } from "../config/env.js";

export const saleorJwksUrl = new URL(
  "/.well-known/jwks.json",
  env.SALEOR_BASE_URL,
);

export const saleorJwks = createRemoteJWKSet(
  saleorJwksUrl,
);