import {
  describe,
  expect,
  it,
} from "vitest";

import {
  mapSaleorOrderToOrderApiRequest,
  SaleorOrderMappingError,
} from "../src/mapper/saleor-order.mapper.js";

import type { SaleorOrderCreated } from "../src/webhook/order-created.schema.js";

const registeredOrder: SaleorOrderCreated = {
  __typename: "OrderCreated",
  issuedAt: "2026-09-10T09:54:16.853488+00:00",
  version: "3.23.31",

  order: {
    id: "T3JkZXI6Yzg2MWFmNmItYzE0OC00ZTYwLWIxNTYtNjBkMGY2NDQxYzA5",
    number: "30",
    created: "2026-09-10T09:54:16.099732+00:00",
    status: "UNFULFILLED",

    userEmail: "admin@example.com",

    user: {
      id: "VXNlcjox",
    },

    total: {
      gross: {
        amount: 50,
        currency: "USD",
      },
    },

    shippingAddress: {
      firstName: "John",
      lastName: "Doe",
      companyName: "",
      streetAddress1: "813 Howard Street",
      streetAddress2: "",
      city: "OSWEGO",
      postalCode: "13126",
      countryArea: "NY",
      country: {
        code: "US",
        country: "United States of America",
      },
      phone: "",
    },

    billingAddress: {
      firstName: "John",
      lastName: "Doe",
      companyName: "",
      streetAddress1: "813 Howard Street",
      streetAddress2: "",
      city: "OSWEGO",
      postalCode: "13126",
      countryArea: "NY",
      country: {
        code: "US",
        country: "United States of America",
      },
      phone: "",
    },

    lines: [
      {
        id: "T3JkZXJMaW5lOjMzMThmYTNlLTYwNzktNDU3ZC05ZGZlLTI1YjhhYjFjMjA2Yw==",
        quantity: 1,
        productName: "Paul's Balance 420",
        variantName: "41",
        productSku: "118223583",

        variant: {
          id: "UHJvZHVjdFZhcmlhbnQ6MzQy",
        },

        unitPrice: {
          gross: {
            amount: 50,
            currency: "USD",
          },
        },

        totalPrice: {
          gross: {
            amount: 50,
            currency: "USD",
          },
        },
      },
    ],
  },
};

describe("Saleor order mapper", () => {
  it("maps a registered Saleor customer to the canonical Order API request", () => {
    const result =
      mapSaleorOrderToOrderApiRequest(
        registeredOrder,
      );

    expect(result).toEqual({
      externalOrderId:
        "T3JkZXI6Yzg2MWFmNmItYzE0OC00ZTYwLWIxNTYtNjBkMGY2NDQxYzA5",

      customer: {
        customerId: "VXNlcjox",
        email: "admin@example.com",
      },

      items: [
        {
          productId:
            "UHJvZHVjdFZhcmlhbnQ6MzQy",
          quantity: 1,
          unitPrice: 50,
        },
      ],

      currency: "USD",

      shippingAddress: {
        addressLine1: "813 Howard Street",
        addressLine2: "",
        city: "OSWEGO",
        postalCode: "13126",
        country: "US",
      },
    });
  });

  it("maps a guest Saleor customer with a null customerId", () => {
    const guestOrder: SaleorOrderCreated = {
      ...registeredOrder,

      order: {
        ...registeredOrder.order,

        userEmail: "guest@example.com",

        user: null,
      },
    };

    const result =
      mapSaleorOrderToOrderApiRequest(
        guestOrder,
      );

    expect(result.customer).toEqual({
      customerId: null,
      email: "guest@example.com",
    });
  });

  it("does not depend on the Saleor product SKU", () => {
    const orderWithoutSku: SaleorOrderCreated = {
      ...registeredOrder,

      order: {
        ...registeredOrder.order,

        lines: registeredOrder.order.lines.map(
          (line) => ({
            ...line,
            productSku: null,
          }),
        ),
      },
    };

    const result =
      mapSaleorOrderToOrderApiRequest(
        orderWithoutSku,
      );

    expect(result.items[0]?.productId).toBe(
      "UHJvZHVjdFZhcmlhbnQ6MzQy",
    );
  });

  it("rejects an order line without a Saleor variant id", () => {
    const orderWithoutVariant: SaleorOrderCreated = {
      ...registeredOrder,

      order: {
        ...registeredOrder.order,

        lines: registeredOrder.order.lines.map(
          (line) => ({
            ...line,
            variant: null,
          }),
        ),
      },
    };

    expect(() =>
      mapSaleorOrderToOrderApiRequest(
        orderWithoutVariant,
      ),
    ).toThrow(SaleorOrderMappingError);
  });
});
