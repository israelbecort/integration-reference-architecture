import { z } from "zod";

const moneySchema = z.object({
  amount: z.number().nonnegative(),
  currency: z.string().length(3),
});

const addressSchema = z.object({
  firstName: z.string(),
  lastName: z.string(),
  companyName: z.string(),
  streetAddress1: z.string(),
  streetAddress2: z.string(),
  city: z.string(),
  postalCode: z.string(),
  countryArea: z.string(),
  country: z.object({
    code: z.string().length(2),
    country: z.string(),
  }),
  phone: z.string(),
});

const orderLineSchema = z.object({
  id: z.string().min(1),
  quantity: z.number().int().positive(),
  productName: z.string().min(1),
  variantName: z.string(),
  productSku: z.string().min(1).nullable(),
  variant: z
    .object({
      id: z.string().min(1),
    })
    .nullable(),

  unitPrice: z.object({
    gross: moneySchema,
  }),

  totalPrice: z.object({
    gross: moneySchema,
  }),
});

export const saleorOrderCreatedSchema = z.object({
  __typename: z.literal("OrderCreated"),

  issuedAt: z.string().datetime({
    offset: true,
  }),

  version: z.string().min(1),

  order: z.object({
    id: z.string().min(1),
    number: z.string().min(1),

    created: z.string().datetime({
      offset: true,
    }),

    status: z.string().min(1),

    userEmail: z.string().email(),
    user: z
      .object({
        id: z.string().min(1),
      })
      .nullable(),

    total: z.object({
      gross: moneySchema,
    }),

    shippingAddress: addressSchema,
    billingAddress: addressSchema,

    lines: z.array(orderLineSchema).min(1),
  }),
});

export type SaleorOrderCreated = z.infer<
  typeof saleorOrderCreatedSchema
>;
