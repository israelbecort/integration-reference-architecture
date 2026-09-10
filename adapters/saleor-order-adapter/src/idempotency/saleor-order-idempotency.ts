import { v5 as uuidv5 } from "uuid";

/*
 * Stable namespace for Saleor order idempotency keys.
 *
 * This value must never change once orders have started using it,
 * otherwise the same Saleor order would generate a different
 * Idempotency-Key.
 */
const SALEOR_ORDER_NAMESPACE =
  "1969b8a6-59a9-5a31-964e-33130c344eb7";

export function createSaleorOrderIdempotencyKey(
  saleorOrderId: string,
): string {
  return uuidv5(
    saleorOrderId,
    SALEOR_ORDER_NAMESPACE,
  );
}
