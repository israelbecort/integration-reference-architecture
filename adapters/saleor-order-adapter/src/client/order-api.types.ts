export type OrderApiCustomer = {
  customerId: string | null;
  email: string;
};

export type OrderApiItem = {
  productId: string;
  quantity: number;
  unitPrice: number;
};

export type OrderApiShippingAddress = {
  addressLine1: string;
  addressLine2: string;
  city: string;
  postalCode: string;
  country: string;
};

export type CreateOrderRequest = {
  externalOrderId: string;
  customer: OrderApiCustomer;
  items: OrderApiItem[];
  currency: string;
  shippingAddress: OrderApiShippingAddress;
};

export type OrderAcceptedResponse = {
  orderId: string;
  externalOrderId: string;
  status: "PROCESSING";
  correlationId: string;
  acceptedAt: string;
};

export type OrderApiProblemDetails = {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  errorCode: string;
  correlationId: string;
  timestamp: string;
};

export type OrderApiRequestContext = {
  idempotencyKey: string;
  correlationId: string;
};