# Banking Transaction Processing System

A concurrent, idempotent, and auditable banking transaction engine built with **Java 21**, **Spring Boot**, **Spring Data JPA**, and **MySQL 8**.

## Tech Stack
- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 4.2.0, Spring Data JPA, Spring Security
- **Database:** MySQL 8.0+
- **Build Tool:** Maven Wrapper (`mvnw`)

## Project Structure (Day 1 Foundation)
- `src/main/resources/db/schema.sql` — Normalized DDL (`customer`, `account`, `transactions`, `transaction_audit`, `failed_transaction`) with PKs, FKs, check constraints, and indexes.
- `src/main/resources/db/seed.sql` — Initial test customers and accounts.
- `src/main/java/com/banking/bankingsystem/entity/` — JPA entity mappings.
- `src/main/java/com/banking/bankingsystem/repository/` — Spring Data JPA repositories with pessimistic row locking (`PESSIMISTIC_WRITE`).
- `src/main/java/com/banking/bankingsystem/exception/` — Domain-specific custom exceptions (`InsufficientBalanceException`, `AccountNotFoundException`, `DuplicateTransactionException`, `InvalidTransactionException`).

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
- [ ] **Day 2:** Role-based security (`ADMIN` / `CUSTOMER`), DTOs, and atomic Transaction Engine (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, idempotency, audit trail)
- [ ] **Day 3:** REST APIs, Global Exception Handler, and Structured Logging
- [ ] **Day 4:** Concurrency Simulator (`1 / 5 / 10 / 20` worker threads, `50,000+` transactions) & SQL Reporting + `EXPLAIN` plans
- [ ] **Day 5:** Automated JUnit 5 Concurrency, Duplicate Idempotency & Rollback Tests
- [ ] **Day 6:** Web UI Dashboard (Customer Portal + Admin Benchmark & Audit Console)
- [ ] **Day 7:** Final Documentation, Architecture/ER Diagrams & End-to-End Verification
