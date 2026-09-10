import {
  flattenedVerify,
  type FlattenedVerifyGetKey,
} from "jose";

import { saleorJwks } from "./saleor-jwks.js";

export class SaleorSignatureError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SaleorSignatureError";
  }
}

export type SaleorSignatureVerifier = (
  rawBody: string | Uint8Array,
  signatureHeader: string,
) => Promise<void>;

export async function verifySaleorWebhookSignature(
  rawBody: string | Uint8Array,
  signatureHeader: string,
  keyResolver: FlattenedVerifyGetKey = saleorJwks,
): Promise<void> {
  const segments = signatureHeader.split(".");

  if (segments.length !== 3) {
    throw new SaleorSignatureError(
      "Invalid Saleor signature format",
    );
  }

  const [
    protectedHeader,
    detachedPayload,
    signature,
  ] = segments;

  if (!protectedHeader || !signature) {
    throw new SaleorSignatureError(
      "Invalid Saleor signature format",
    );
  }

  if (detachedPayload !== "") {
    throw new SaleorSignatureError(
      "Expected detached Saleor JWS payload",
    );
  }

  try {
    const verification = await flattenedVerify(
      {
        protected: protectedHeader,
        payload: rawBody,
        signature,
      },
      keyResolver,
      {
        algorithms: ["RS256"],
      },
    );

    const header = verification.protectedHeader;

    if (header?.alg !== "RS256") {
      throw new SaleorSignatureError(
        "Unsupported Saleor signature algorithm",
      );
    }

    if (header.b64 !== false) {
      throw new SaleorSignatureError(
        "Saleor JWS must use an unencoded payload",
      );
    }

    if (
      !Array.isArray(header.crit) ||
      !header.crit.includes("b64")
    ) {
      throw new SaleorSignatureError(
        "Saleor JWS is missing the b64 critical header",
      );
    }

    if (
      typeof header.kid !== "string" ||
      header.kid.length === 0
    ) {
      throw new SaleorSignatureError(
        "Saleor JWS is missing kid",
      );
    }
  } catch (error) {
    if (error instanceof SaleorSignatureError) {
      throw error;
    }

    throw new SaleorSignatureError(
      "Invalid Saleor webhook signature",
    );
  }
}