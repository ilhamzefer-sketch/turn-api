update payment_sessions
set status = 'CANCELLED',
    completed_at = coalesce(completed_at, current_timestamp),
    external_order_password = null
where payment_purpose = 'PROVIDER_SUBSCRIPTION'
  and status = 'PENDING';
