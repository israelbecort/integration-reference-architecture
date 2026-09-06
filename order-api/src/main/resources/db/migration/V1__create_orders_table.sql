CREATE TABLE orders (
                        order_id UUID NOT NULL,
                        accepted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                        correlation_id UUID NOT NULL,
                        external_order_id VARCHAR(100) NOT NULL,
                        idempotency_key UUID NOT NULL,
                        request_hash VARCHAR(64) NOT NULL,
                        status VARCHAR(30) NOT NULL,

                        CONSTRAINT orders_pkey
                            PRIMARY KEY (order_id),

                        CONSTRAINT uk_orders_external_order_id
                            UNIQUE (external_order_id),

                        CONSTRAINT uk_orders_idempotency_key
                            UNIQUE (idempotency_key),

                        CONSTRAINT orders_status_check
                            CHECK (
                                status IN (
                                           'ACCEPTED',
                                           'PROCESSING',
                                           'COMPLETED',
                                           'FAILED'
                                    )
                                )
);