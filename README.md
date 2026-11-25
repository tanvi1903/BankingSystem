# Banking Transaction Processing System

A concurrent, idempotent, and auditable banking transaction engine built with **Java 21**, **Spring Boot**, **Spring Data JPA**, and **MySQL 8**.

## Tech Stack
- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 4.2.0, Spring Data JPA, Spring Security
- **Database:** MySQL 8.0+
- **Build Tool:** Maven Wrapper (`mvnw`)

## Project Structure
- `src/main/resources/db/schema.sql` — Normalized DDL (`customer`, `account`, `transactions`, `transaction_audit`, `failed_transaction`) with PKs, FKs, check constraints, and indexes.
- `src/main/resources/db/seed.sql` — Initial test customers and accounts.
- `src/main/java/com/banking/bankingsystem/entity/` — JPA entity mappings.
- `src/main/java/com/banking/bankingsystem/repository/` — Spring Data JPA repositories with pessimistic row locking (`PESSIMISTIC_WRITE`).
- `src/main/java/com/banking/bankingsystem/exception/` — Domain-specific custom exceptions (`InsufficientBalanceException`, `AccountNotFoundException`, `DuplicateTransactionException`, `InvalidTransactionException`).
- `src/main/java/com/banking/bankingsystem/config/` — Role-based Spring Security configuration (`ADMIN` vs `CUSTOMER`).
- `src/main/java/com/banking/bankingsystem/dto/` — Validated request and response DTOs (`TransactionRequest`, `TransactionResponse`, `AccountResponse`).
- `src/main/java/com/banking/bankingsystem/service/` — Atomic deadlock-free `TransactionServiceImpl`, `AccountService`, and `REQUIRES_NEW` rollback-safe `AuditService`.

## Default Security Credentials (RBAC)
| Role | Username | Password | Access Scope |
|---|---|---|---|
| **Bank Manager (`ROLE_ADMIN`)** | `admin` | `admin123` | Full system access: Failed transactions, audit logs, daily reports, all accounts, concurrency simulator |
| **Customer (`ROLE_CUSTOMER`)** | `customer1` | `cust123` | View accounts, deposit, withdraw, transfer, view transaction history |
| **Customer (`ROLE_CUSTOMER`)** | `customer2` | `cust123` | View accounts, deposit, withdraw, transfer, view transaction history |

## Local Setup & Run Instructions

### Prerequisites
- JDK 21 installed and configured in `JAVA_HOME`
- MySQL Server 8.0+ running on `localhost:3306`

### Steps to Run
1. Clone the repository and open the `banking-system` folder.
2. Configure your MySQL credentials in `src/main/resources/application.properties`:
   - `spring.datasource.url=jdbc:mysql://localhost:3306/banking_db?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false`
   - `spring.datasource.username=root`
   - `spring.datasource.password=YOUR_MYSQL_PASSWORD`
   - `server.port=8081`
3. Start the application using Maven Wrapper:
   `.\mvnw.cmd spring-boot:run`
4. Verify the server starts cleanly on `http://localhost:8081` and initializes the `banking_db` schema and seed records.

## Roadmap
- [x] **Day 1:** Database schema, seed data, JPA entities, pessimistic locking repositories, custom exceptions
- [x] **Day 2:** Role-based security (`ADMIN` / `CUSTOMER`), DTOs, rollback-safe failure auditing (`REQUIRES_NEW`), and atomic deadlock-free Transaction Engine (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, idempotency)
- [ ] **Day 3:** REST APIs, Global Exception Handler, and Structured Logging
- [ ] **Day 4:** Concurrency Simulator (`1 / 5 / 10 / 20` worker threads, `50,000+` transactions) & SQL Reporting + `EXPLAIN` plans
- [ ] **Day 5:** Automated JUnit 5 Concurrency, Duplicate Idempotency & Rollback Tests
- [ ] **Day 6:** Web UI Dashboard (Customer Portal + Admin Benchmark & Audit Console)
- [ ] **Day 7:** Final Documentation, Architecture/ER Diagrams & End-to-End Verification
---

## Day 3: REST APIs, Global Exception Handling & Structured Logging

### 1. API Endpoints Overview

#### Account APIs (`CUSTOMER` / `ADMIN`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/accounts` | List all bank accounts |
| `GET` | `/api/accounts/{accountId}` | Get account details by ID |
| `GET` | `/api/accounts/customer/{customerId}` | List all accounts for a customer |
| `GET` | `/api/accounts/{accountId}/balance` | Check real-time balance and status |

#### Transaction APIs (`CUSTOMER` / `ADMIN`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/transactions/process` | Process `DEPOSIT`, `WITHDRAWAL`, or `TRANSFER` |
| `POST` | `/api/transactions/deposit` | Deposit funds into target account |
| `POST` | `/api/transactions/withdraw` | Withdraw funds from source account |
| `POST` | `/api/transactions/transfer` | Atomic transfer with deadlock-safe locking |
| `GET` | `/api/transactions/account/{accountId}` | View transaction history for an account |
| `GET` | `/api/transactions` | List all processed transactions |

#### Admin Reporting APIs (`ADMIN` Only)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/admin/failed-transactions` | View all rejected/failed transactions |
| `GET` | `/api/admin/audits` | View complete audit trail (`beforeBalance`, `afterBalance`, `threadName`, `durationMs`) |
| `GET` | `/api/admin/summary` | View system-wide liquidity, success/failure counts, and average execution latency |

### 2. Standardized Error Responses & Correlation Tracing
- Every request receives an `X-Correlation-ID` response header and logs `[corrId=... user=...]` via SLF4J MDC.
- Domain exceptions map cleanly via `@RestControllerAdvice` (`GlobalExceptionHandler`):
  - `400 Bad Request`: `INSUFFICIENT_BALANCE`, `INVALID_TRANSACTION`, `VALIDATION_FAILED`
  - `403 Forbidden`: `ACCESS_DENIED`
  - `404 Not Found`: `ACCOUNT_NOT_FOUND`
  - `409 Conflict`: `DUPLICATE_TRANSACTION`