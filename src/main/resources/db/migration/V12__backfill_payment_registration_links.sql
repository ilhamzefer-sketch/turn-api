update payment_sessions payment
set registration_id = (
    select min(registration.id)
    from registrations registration
    where lower(registration.email) = lower(payment.email)
)
where payment.registration_id is null
  and exists (
    select 1
    from registrations registration
    where lower(registration.email) = lower(payment.email)
  );
