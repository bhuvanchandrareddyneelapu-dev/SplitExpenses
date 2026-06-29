# SplitWiseMoney - Setup & Execution Instructions

SplitWiseMoney is a production-ready expense sharing web application built using Spring Boot, Spring Security + JWT, Spring Data JPA, and H2 (development) / PostgreSQL (production).

## System Requirements

- **Java JDK**: Version 26 (located at `c:\Expense Splitting\jdk-26`)
- **Maven**: Version 3.x (or use the packaged Maven Wrapper `mvnw`)
- **Web Browser**: Modern browser supporting JavaScript and CSS

---

## Environment Setup

To build and run the application, ensure your environment variables are configured correctly. You can set them temporarily in your terminal session or permanently in your system.

### PowerShell Setup (Recommended)
```powershell
$env:JAVA_HOME="c:\Expense Splitting\jdk-26"
$env:PATH="c:\Expense Splitting\jdk-26\bin;" + $env:PATH
```

### Windows Command Prompt (CMD) Setup
```cmd
set JAVA_HOME=c:\Expense Splitting\jdk-26
set PATH=%JAVA_HOME%\bin;%PATH%
```

---

## Build & Test Instructions

Use the Maven Wrapper (`mvnw`) in the root directory to clean, compile, and execute test cases.

### 1. Compile the Project
```bash
.\mvnw.cmd clean compile
```

### 2. Run Integration Tests
Execute the JUnit integration tests to verify debt settlement and security logic:
```bash
.\mvnw.cmd test
```

---

## Running the Application

### 1. Run via Spring Boot Maven Plugin
Start the development server using H2 in-memory database:
```bash
.\mvnw.cmd spring-boot:run
```

Once started, the application will bind to port **8080** and serve the static glassmorphism frontend at `http://localhost:8080/`.

### 2. Packaging the Application
To build a production-ready runnable JAR file:
```bash
.\mvnw.cmd clean package
```
The compiled jar will be available under `target/splitwisemoney-0.0.1-SNAPSHOT.jar`. Run it directly via:
```bash
java -jar target/splitwisemoney-0.0.1-SNAPSHOT.jar
```

---

## Accessing Endpoints

- **Web Application Interface**: Open [http://localhost:8080/](http://localhost:8080/) in your browser.
- **Swagger Open API UI**: Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) to interact with and test all protected and public API endpoints.
- **H2 Console**: Open [http://localhost:8080/h2-console/](http://localhost:8080/h2-console/) (if enabled, JDBC URL: `jdbc:h2:mem:splitwisedb`, username: `sa`, no password).
