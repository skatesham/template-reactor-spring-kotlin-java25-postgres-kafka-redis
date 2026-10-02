# Integrações

Integrações externas usam Ports and Adapters.

## Port

```kotlin
interface PaymentGateway {
    fun charge(request: PaymentRequest): PaymentResult
}
```

## Adapter

```text
HttpPaymentGateway
StripePaymentGateway
MercadoPagoPaymentGateway
```

O caso de uso depende da interface, não da tecnologia.

## Anti-Corruption Layer

Ao integrar modelos externos:

```text
External API
   ↓
External DTO
   ↓
Mapper / ACL
   ↓
Domain Model
```

Objetos externos não devem entrar diretamente no domínio.
