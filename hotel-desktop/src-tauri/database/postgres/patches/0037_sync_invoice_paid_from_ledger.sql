-- Sync invoice paid_amount / status / paid_at from customer ledger receipts.
--
-- Checkout often leaves an `issued` invoice stub with paid_amount = 0 for a
-- company (city ledger) stay. Staff later settle the receivable via Finance
-- → Company Ledger, which updates customer_ledgers but historically left the
-- invoices row looking unpaid (e.g. INV-202610-0175, INV-202610-0156,
-- INV-202611-0306). Application code now syncs on payment create/update/delete;
-- this one-off backfill catches already-settled ledgers.
--
-- Idempotent: only rows whose paid_amount / status / paid_at actually change
-- are updated. Void and refunded invoices are left alone. balance_due is a
-- generated column (total_amount - paid_amount).

UPDATE public.invoices AS i
SET paid_amount = src.paid_amount,
    status = CASE
        WHEN src.paid_amount >= i.total_amount THEN 'paid'
        WHEN i.status = 'paid' THEN 'issued'
        ELSE i.status
    END,
    paid_at = CASE
        WHEN src.paid_amount >= i.total_amount THEN COALESCE(i.paid_at, CURRENT_TIMESTAMP)
        ELSE NULL
    END,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT ON (cl.invoice_number)
        cl.invoice_number,
        cl.booking_id,
        cl.paid_amount
    FROM public.customer_ledgers cl
    WHERE cl.post_type = 'room_charge'
      AND cl.status <> 'void'
      AND cl.void_at IS NULL
      AND COALESCE(cl.is_reversal, FALSE) = FALSE
      AND cl.invoice_number IS NOT NULL
      AND BTRIM(cl.invoice_number) <> ''
    ORDER BY cl.invoice_number, cl.created_at DESC, cl.id DESC
) src
WHERE i.invoice_number = src.invoice_number
  AND i.status NOT IN ('void', 'refunded')
  AND (
        i.paid_amount IS DISTINCT FROM src.paid_amount
     OR i.status IS DISTINCT FROM (
            CASE
                WHEN src.paid_amount >= i.total_amount THEN 'paid'
                WHEN i.status = 'paid' THEN 'issued'
                ELSE i.status
            END
        )
     OR i.paid_at IS DISTINCT FROM (
            CASE
                WHEN src.paid_amount >= i.total_amount THEN COALESCE(i.paid_at, CURRENT_TIMESTAMP)
                ELSE NULL
            END
        )
  );

-- Ledgers whose invoice_number is missing or does not match an invoices row
-- still update the booking's latest non-void invoice when the ledger is paid.
UPDATE public.invoices AS i
SET paid_amount = src.paid_amount,
    status = CASE
        WHEN src.paid_amount >= i.total_amount THEN 'paid'
        WHEN i.status = 'paid' THEN 'issued'
        ELSE i.status
    END,
    paid_at = CASE
        WHEN src.paid_amount >= i.total_amount THEN COALESCE(i.paid_at, CURRENT_TIMESTAMP)
        ELSE NULL
    END,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT ON (cl.booking_id)
        cl.booking_id,
        cl.paid_amount
    FROM public.customer_ledgers cl
    WHERE cl.post_type = 'room_charge'
      AND cl.status <> 'void'
      AND cl.void_at IS NULL
      AND COALESCE(cl.is_reversal, FALSE) = FALSE
      AND cl.booking_id IS NOT NULL
    ORDER BY cl.booking_id, cl.created_at DESC, cl.id DESC
) src
WHERE i.booking_id = src.booking_id
  AND i.status NOT IN ('void', 'refunded')
  AND NOT EXISTS (
      SELECT 1
      FROM public.customer_ledgers cl2
      WHERE cl2.invoice_number = i.invoice_number
        AND cl2.post_type = 'room_charge'
        AND cl2.status <> 'void'
        AND cl2.void_at IS NULL
        AND COALESCE(cl2.is_reversal, FALSE) = FALSE
  )
  AND (
        i.paid_amount IS DISTINCT FROM src.paid_amount
     OR i.status IS DISTINCT FROM (
            CASE
                WHEN src.paid_amount >= i.total_amount THEN 'paid'
                WHEN i.status = 'paid' THEN 'issued'
                ELSE i.status
            END
        )
     OR i.paid_at IS DISTINCT FROM (
            CASE
                WHEN src.paid_amount >= i.total_amount THEN COALESCE(i.paid_at, CURRENT_TIMESTAMP)
                ELSE NULL
            END
        )
  );
