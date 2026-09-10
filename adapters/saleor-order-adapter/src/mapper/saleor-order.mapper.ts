import type { CreateOrderRequest } from "../client/order-api.types.js";
import type { SaleorOrderCreated } from "../webhook/order-created.schema.js";

export class SaleorOrderMappingError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SaleorOrderMappingError";
  }
}

export function mapSaleorOrderToOrderApiRequest(
  event: SaleorOrderCreated,
): CreateOrderRequest {
  const order = event.order;

  const items = order.lines.map((line) => {
    const productId = line.variant?.id;

    if (!productId) {
      throw new SaleorOrderMappingError(
        `Saleor order line ${line.id} does not contain a variant id`,
      );
    }

    return {
      productId,
      quantity: line.quantity,
      unitPrice: line.unitPrice.gross.amount,
    };
  });

  return {
    externalOrderId: order.id,

    customer: {
      customerId: order.user?.id ?? null,
      email: order.userEmail,
    },

    items,

    currency: order.total.gross.currency,

    shippingAddress: {
      addressLine1:
        order.shippingAddress.streetAddress1,

      addressLine2:
        order.shippingAddress.streetAddress2,

      city:
        order.shippingAddress.city,

      postalCode:
        order.shippingAddress.postalCode,

      country:
        order.shippingAddress.country.code,
    },
  };
}
