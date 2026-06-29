# SplitWiseMoney - PostgreSQL Production Deployment Guide

This guide details instructions to deploy SplitWiseMoney to a production environment using a dedicated **PostgreSQL** database.

---

## 1. Database Setup (PostgreSQL)

Before running the application in production, you must provision a PostgreSQL database instance.

### Create Database and User
Log in to your PostgreSQL instance (e.g., via `psql` or pgAdmin) and run:

```sql
CREATE DATABASE splitwisemoney;
CREATE USER splitwise_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE splitwisemoney TO splitwise_user;
```

---

## 2. Configuration (`application-prod.properties`)

The application is packaged with a `prod` profile configured in [application-prod.properties](file:///c:/Expense%20Splitting/src/main/resources/application-prod.properties). It externalizes sensitive configuration values using environment variables.

To start the application, inject the following environment variables:

| Environment Variable | Description | Example Value |
| :--- | :--- | :--- |
| `DB_HOST` | Host address of PostgreSQL server | `postgres-server.mycompany.internal` |
| `DB_PORT` | Port of PostgreSQL server (default: 5432) | `5432` |
| `DB_NAME` | Database name (default: splitwisemoney) | `splitwisemoney` |
| `DB_USER` | PostgreSQL Username | `splitwise_user` |
| `DB_PASSWORD` | PostgreSQL Password | `your_secure_password` |
| `PORT` | Web Server Port (default: 8080) | `80` |
| `JWT_SECRET` | Strong HS512 JWT Signature Secret | `9a4f2c8d3b7a1e5f8c6d4...` |

---

## 3. Database Migrations (Flyway)

SplitWiseMoney handles database migrations using **Flyway**. When the production profile is activated, Flyway will:
1. Connect to the specified PostgreSQL database.
2. Scan for schema history. If the table `flyway_schema_history` does not exist, it initializes it.
3. Apply migration scripts located under `db/migration/` sequentially.
4. Set up tables: `users`, `groups`, `group_members`, `expenses`, `expense_participants`, `settlements`, `activity_logs`, `notifications`.

---

## 4. Run Options

### Option A: Standard Runnable JAR Execution (Recommended)
1. Package the application:
   ```bash
   .\mvnw.cmd clean package -DskipTests
   ```
2. Export environmental variables in your terminal:
   ```bash
   # Linux/macOS
   export DB_HOST="localhost"
   export DB_USER="splitwise_user"
   export DB_PASSWORD="your_secure_password"
   export JWT_SECRET="your_very_long_jwt_signing_secret"
   
   # Windows PowerShell
   $env:DB_HOST="localhost"
   $env:DB_USER="splitwise_user"
   $env:DB_PASSWORD="your_secure_password"
   $env:JWT_SECRET="your_very_long_jwt_signing_secret"
   ```
3. Run the jar activating the `prod` profile:
   ```bash
   java -jar target/splitwisemoney-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
   ```

### Option B: Docker Container Deployment
You can package and run SplitWiseMoney as a Docker image.

1. **Create a `Dockerfile`** in the project root:
   ```dockerfile
   FROM eclipse-temurin:21-jre-alpine
   VOLUME /tmp
   COPY target/splitwisemoney-0.0.1-SNAPSHOT.jar app.jar
   ENTRYPOINT ["java","-jar","/app.jar","--spring.profiles.active=prod"]
   ```
2. Build the image:
   ```bash
   docker build -t splitwisemoney:latest .
   ```
3. Run container injecting environment variables:
   ```bash
   docker run -d -p 80:8080 \
     -e DB_HOST="postgres-db-host" \
     -e DB_USER="splitwise_user" \
     -e DB_PASSWORD="your_secure_password" \
     -e JWT_SECRET="your_very_long_jwt_signing_secret" \
     splitwisemoney:latest
   ```

---

## 5. Security Checklist
- [ ] **SSL/TLS**: Ensure the application is deployed behind a reverse proxy (like Nginx, Apache, or AWS ALB) that terminates SSL/TLS (HTTPS) to encrypt traffic.
- [ ] **Secrets**: Never commit raw database passwords or JWT signing keys to the source repository. Always load them via environment variables or secret vaults.
- [ ] **JWT Key**: Choose a signing secret containing at least 256 bits of key material (HS512 is used by default).
