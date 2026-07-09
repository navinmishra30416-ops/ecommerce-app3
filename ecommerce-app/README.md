# E-commerce backend (Spring Boot)

A modular-monolith e-commerce backend: product catalog, cart, checkout with
inventory reservation, JWT auth, and payment integration point.

## Stack
- Java 25, Spring Boot 3.3
- PostgreSQL + Flyway (schema-managed migrations, no Hibernate auto-ddl)
- Redis (wired in, not yet used — good fit for cart caching or rate limiting)
- Spring Security + JWT
- Maven

## Running locally

1. Start Postgres and Redis:
   ```bash
   docker-compose up -d
   ```

2. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway runs the migration in `src/main/resources/db/migration/V1__init_schema.sql`
   automatically on startup.

3. The API is at `http://localhost:8080/api`.

## Environment variables
| Variable      | Default                         | Purpose                  |
|---------------|----------------------------------|---------------------------|
| DB_USERNAME   | postgres                        | Postgres user             |
| DB_PASSWORD   | postgres                        | Postgres password         |
| REDIS_HOST    | localhost                       | Redis host                |
| REDIS_PORT    | 6379                            | Redis port                |
| JWT_SECRET    | (dev default — override in prod)| HMAC signing key for JWT  |

**Set a real `JWT_SECRET` before deploying anywhere near production.**

## API overview

### Auth (public)
- `POST /api/auth/register` — `{ email, password, firstName, lastName }`
- `POST /api/auth/login` — `{ email, password }` → `{ token, email, role }`

Send the token on every other request: `Authorization: Bearer <token>`

### Products
- `GET /api/products?categoryId=&search=&page=&size=` — public
- `GET /api/products/{id}` — public
- `POST /api/products` — admin only
- `PUT /api/products/{id}` — admin only
- `DELETE /api/products/{id}` — admin only (soft-deactivates)

### Cart (authenticated)
- `GET /api/cart`
- `POST /api/cart/items` — `{ productId, quantity }`
- `PUT /api/cart/items/{productId}?quantity=N`
- `DELETE /api/cart/items/{productId}`

### Orders (authenticated)
- `POST /api/orders/checkout` — `{ addressId }`
- `GET /api/orders` — order history for the logged-in user
- `GET /api/orders/{id}`

### Reviews
- `GET /api/reviews/product/{productId}` — public
- `POST /api/reviews` — authenticated, `{ productId, rating, comment }`

## Design notes worth knowing before you extend this

- **Inventory is separate from products** and updated under a pessimistic
  row lock during checkout (`InventoryRepository.findByProductIdForUpdate`).
  This is what stops two concurrent checkouts from both selling the last
  unit of a product — read the comments in `OrderService.checkout()`.
- **`order_items` snapshots the price at purchase time.** Orders must stay
  historically accurate even if the product's price changes later.
- **Payment is a stub** (`PaymentService`). Swap its body for a real
  Stripe/Razorpay SDK call — never store raw card data yourself, only the
  provider's transaction reference.
- **Cart currently lives in Postgres** (`carts` / `cart_items` tables). For
  higher traffic, moving the cart to Redis (keyed by user id) and only
  persisting to `orders` at checkout is a common next step — it removes
  a lot of write load from the primary database.
- **Method-level authorization** uses `@PreAuthorize("hasRole('ADMIN')")`
  on top of the URL-pattern rules in `SecurityConfig` — both layers exist
  so a stray missing pattern in `SecurityConfig` doesn't silently expose
  an admin endpoint.

## What's not included (next steps)
- Pagination/sorting defaults and API versioning conventions
- Rate limiting on auth endpoints
- Email notifications (order confirmation, password reset)
- Admin endpoints for managing categories/reviews moderation
- Integration tests (Testcontainers + Postgres is the natural fit here)
- OpenAPI/Swagger docs (`springdoc-openapi-starter-webmvc-ui` drops in easily)
