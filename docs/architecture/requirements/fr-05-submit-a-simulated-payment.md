# FR-05 — Submit a simulated payment

## Actor

A customer using an open hosted checkout session.

## Precondition

The checkout session exists, is `OPEN`, has not expired, and has not already produced a successful payment.

## Trigger

The customer provides a valid email address and selects a supported predefined simulator scenario.

## Required behavior

Striply creates a payment intent with a public identifier beginning with `pi_` using the checkout session's immutable amount and currency snapshot. It processes the selected scenario without collecting or storing real card or bank data. The payment intent follows defined state transitions and records the simulated provider outcome. A successful outcome creates one payment with a public identifier beginning with `pay_` and marks the checkout session `COMPLETED`. Declines and insufficient-funds scenarios produce a failed outcome. Delayed-processing and provider-timeout scenarios produce the documented non-final outcome until simulation processing resolves.

Striply must prevent simultaneous submissions for the same checkout session from producing more than one successful payment.

## Successful outcome

The customer receives a result containing the checkout-session status and the payment outcome, then may continue to the approved success URL.

## Failure or alternate outcomes

Striply rejects payment submission when the session is missing, expired, canceled, completed, or otherwise not payable; the email or scenario is invalid; or the organization is inactive. A failed or timed-out simulation must not be reported as a successful payment. Responses expose no simulator internals, credentials, or private merchant information.
