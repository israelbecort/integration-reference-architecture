import {
  createLocalJWKSet,
  exportJWK,
  FlattenedSign,
  generateKeyPair,
  type FlattenedVerifyGetKey,
} from "jose";

import {
  beforeAll,
  describe,
  expect,
  it,
} from "vitest";

import {
  SaleorSignatureError,
  verifySaleorWebhookSignature,
} from "../src/security/saleor-signature.js";

const TEST_KID = "saleor-test-key";

const RAW_BODY = JSON.stringify({
  __typename: "OrderCreated",
  order: {
    id: "test-order-id",
    number: "100",
  },
});

let privateKey: Awaited<
  ReturnType<typeof generateKeyPair>
>["privateKey"];

let keyResolver: FlattenedVerifyGetKey;

beforeAll(async () => {
  const keyPair = await generateKeyPair("RS256", {
    extractable: true,
  });

  privateKey = keyPair.privateKey;

  const publicJwk = await exportJWK(
    keyPair.publicKey,
  );

  keyResolver = createLocalJWKSet({
    keys: [
      {
        ...publicJwk,
        kid: TEST_KID,
        alg: "RS256",
        use: "sig",
      },
    ],
  });
});

async function createSignature(
  rawBody: string,
  kid = TEST_KID,
): Promise<string> {
  const jws = await new FlattenedSign(
    new TextEncoder().encode(rawBody),
  )
    .setProtectedHeader({
      alg: "RS256",
      kid,
      b64: false,
      crit: ["b64"],
    })
    .sign(privateKey);

  if (!jws.protected) {
    throw new Error(
      "Test JWS protected header was not generated",
    );
  }

  return `${jws.protected}..${jws.signature}`;
}

describe("Saleor webhook signature verification", () => {
  it("accepts a valid RS256 Saleor-style signature", async () => {
    const signature = await createSignature(
      RAW_BODY,
    );

    await expect(
      verifySaleorWebhookSignature(
        RAW_BODY,
        signature,
        keyResolver,
      ),
    ).resolves.toBeUndefined();
  });

  it("rejects the signature when the payload is modified", async () => {
    const signature = await createSignature(
      RAW_BODY,
    );

    const modifiedBody = `${RAW_BODY} `;

    await expect(
      verifySaleorWebhookSignature(
        modifiedBody,
        signature,
        keyResolver,
      ),
    ).rejects.toBeInstanceOf(
      SaleorSignatureError,
    );
  });

  it("rejects a signature with an unknown kid", async () => {
    const signature = await createSignature(
      RAW_BODY,
      "unknown-key",
    );

    await expect(
      verifySaleorWebhookSignature(
        RAW_BODY,
        signature,
        keyResolver,
      ),
    ).rejects.toBeInstanceOf(
      SaleorSignatureError,
    );
  });

  it("rejects an invalid JWS format", async () => {
    await expect(
      verifySaleorWebhookSignature(
        RAW_BODY,
        "invalid-signature",
        keyResolver,
      ),
    ).rejects.toBeInstanceOf(
      SaleorSignatureError,
    );
  });
});
