# Testing Best Practices for Spring Boot Microservices

Here is a comprehensive overview of the recommended tech stack, patterns, and best practices for testing Spring Boot microservices.

---

Here is a comprehensive **Table of Contents** for the `tests.md` file:

# Table of Contents

1. [Testing Pyramid](#-testing-pyramid)
2. [Recommended Tech Stack](#-recommended-tech-stack)
3. [Unit Tests — Service Layer](#1️⃣-unit-tests--service-layer)
4. [Web Layer Tests — Controllers](#2️⃣-web-layer-tests--controllers)
5. [Repository Tests — Data Layer](#3️⃣-repository-tests--data-layer)
6. [Integration Tests — Testcontainers (Real DB)](#4️⃣-integration-tests--testcontainers-real-db)
7. [Feign Client Tests — WireMock](#5️⃣-feign-client-tests--wiremock)
8. [Exception Handler Tests](#6️⃣-exception-handler-tests)
9. [Code Coverage with JaCoCo](#7️⃣-code-coverage-with-jacoco)
10. [Industry Patterns Summary](#-industry-patterns-summary)
11. [Recommended Coverage Targets](#-recommended-coverage-targets)
12. [Test Annotations Reference](#-test-annotations-reference)
    - [JUnit 5 (Jupiter) Annotations](#-junit-5-jupiter-annotations)
    - [Mockito Annotations](#-mockito-annotations)
    - [Spring Boot Test Slices](#-spring-boot-test-slices)
    - [Spring Boot Test Utility Annotations](#-spring-boot-test-utility-annotations)
    - [Testcontainers Annotations](#-testcontainers-annotations)
    - [Additional Useful Annotations](#-additional-useful-annotations)
    - [Annotation Combinations Cheat Sheet](#-annotation-combinations-cheat-sheet)

---

## 🧪 Testing Pyramid

A healthy test suite follows the **Testing Pyramid** principle:

```
/\
       /  \
      / E2E\        ← Few, slow, expensive
     /------\
    /  Integ  \     ← Moderate, test boundaries
   /------------\
  /  Unit Tests  \  ← Many, fast, cheap
 /________________\
```


---

## 🛠️ Recommended Tech Stack

| Layer                        | Tool                  | Dependency                                   |
|------------------------------|-----------------------|----------------------------------------------|
| **Unit Testing**             | JUnit 5 (Jupiter)     | `spring-boot-starter-test`                   |
| **Mocking**                  | Mockito               | `spring-boot-starter-test`                   |
| **Web Layer**                | MockMvc               | `spring-boot-starter-test`                   |
| **Assertions**               | AssertJ               | `spring-boot-starter-test`                   |
| **JSON Assertions**          | JSONAssert / Hamcrest | `spring-boot-starter-test`                   |
| **DB Integration**           | `@DataJpaTest` + H2   | `com.h2database:h2` (test scope)             |
| **Full Context**             | `@SpringBootTest`     | `spring-boot-starter-test`                   |
| **HTTP Mocking**             | WireMock              | `org.wiremock:wiremock-standalone`           |
| **Contract Testing**         | Spring Cloud Contract | `spring-cloud-starter-contract-verifier`     |
| **Integration (containers)** | Testcontainers        | `org.testcontainers:mysql` + `junit-jupiter` |
| **Coverage**                 | JaCoCo                | `jacoco-maven-plugin`                        |

All of the above (except Testcontainers and WireMock) are **included** in `spring-boot-starter-test`.

---

## 1️⃣ Unit Tests — Service Layer

Use **Mockito** to isolate the class under test. No Spring context is loaded — these are fast.

```java
@ExtendWith(MockitoExtension.class)
class AccountsServiceImplTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AccountsServiceImpl accountsService;

    @Test
    void createAccount_shouldSaveCustomerAndAccount() {
        // Given
        CustomerDto dto = new CustomerDto();
        dto.setMobileNumber("9876543210");
        dto.setName("Jane Doe");
        dto.setEmail("jane@example.com");

        when(customerRepository.findByMobileNumber("9876543210"))
            .thenReturn(Optional.empty());

        // When
        accountsService.createAccount(dto);

        // Then
        verify(customerRepository).save(any(Customer.class));
        verify(accountsRepository).save(any(Accounts.class));
    }

    @Test
    void createAccount_shouldThrow_whenCustomerAlreadyExists() {
        CustomerDto dto = new CustomerDto();
        dto.setMobileNumber("9876543210");

        when(customerRepository.findByMobileNumber("9876543210"))
            .thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> accountsService.createAccount(dto))
            .isInstanceOf(CustomerAlreadyExistsException.class);
    }
}
```


---

## 2️⃣ Web Layer Tests — Controllers

Use `@WebMvcTest` to load **only** the web layer. All service dependencies must be mocked with `@MockBean`.

```java
@WebMvcTest(AccountsController.class)
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IAccountsService accountsService;

    @Test
    void createAccount_shouldReturn201() throws Exception {
        CustomerDto dto = new CustomerDto();
        dto.setName("Jane Doe");
        dto.setEmail("jane@example.com");
        dto.setMobileNumber("9876543210");

        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201"));
    }

    @Test
    void createAccount_shouldReturn400_whenMobileNumberInvalid() throws Exception {
        CustomerDto dto = new CustomerDto();
        dto.setName("Jane Doe");
        dto.setEmail("jane@example.com");
        dto.setMobileNumber("123"); // invalid

        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isBadRequest());
    }
}
```


---

## 3️⃣ Repository Tests — Data Layer

Use `@DataJpaTest` which loads only JPA components and auto-configures an **in-memory H2 database**. Flyway/Liquibase can be disabled or replaced with `@Sql` scripts.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // uses H2
class AccountsRepositoryTest {

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByCustomerId_shouldReturnAccount() {
        // Given
        Accounts account = new Accounts();
        account.setAccountNumber(1234567890L);
        account.setCustomerId(1L);
        account.setAccountType("Savings");
        account.setBranchAddress("123 Main St");
        entityManager.persist(account);
        entityManager.flush();

        // When
        Optional<Accounts> result = accountsRepository.findByCustomerId(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAccountType()).isEqualTo("Savings");
    }
}
```


> **Tip:** If you want to test against **real MySQL** to catch dialect-specific issues, use **Testcontainers** (see below).

---

## 4️⃣ Integration Tests — Testcontainers (Real DB)

Testcontainers spins up a **real Docker container** for MySQL during tests, giving you production-like fidelity.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```


```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountsIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:lts")
        .withDatabaseName("accountsdb")
        .withUsername("testuser")
        .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndFetchAccount_fullFlow() throws Exception {
        // POST to create
        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Jane Doe",
                      "email": "jane@example.com",
                      "mobileNumber": "9876543210"
                    }
                    """))
            .andExpect(status().isCreated());

        // GET to fetch
        mockMvc.perform(get("/api/fetch")
                .param("mobileNumber", "9876543210"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mobileNumber").value("9876543210"));
    }
}
```


---

## 5️⃣ Feign Client Tests — WireMock

Mock external HTTP services (Cards, Loans) with **WireMock** to test Feign client behavior in isolation.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <scope>test</scope>
</dependency>
```


```java
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0) // random port
@TestPropertySource(properties = {
    "spring.cloud.openfeign.client.config.cards.url=http://localhost:${wiremock.server.port}",
    "spring.cloud.openfeign.client.config.loans.url=http://localhost:${wiremock.server.port}"
})
class CustomerControllerFeignTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchCustomerDetails_shouldAggregateFromDownstreamServices() throws Exception {
        // Stub Cards service
        stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo("9876543210"))
            .willReturn(okJson("""
                { "mobileNumber": "9876543210", "cardNumber": "4111111111111111" }
                """)));

        // Stub Loans service
        stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo("9876543210"))
            .willReturn(okJson("""
                { "mobileNumber": "9876543210", "loanNumber": "100000000001" }
                """)));

        mockMvc.perform(get("/api/fetchCustomerDetails")
                .param("mobileNumber", "9876543210")
                .header("eazybank-correlation-id", "test-corr-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cardsDto.cardNumber").value("4111111111111111"));
    }
}
```


---

## 6️⃣ Exception Handler Tests

Test your `GlobalExceptionHandler` scenarios through `@WebMvcTest`:

```java
@WebMvcTest(AccountsController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IAccountsService accountsService;

    @Test
    void fetchAccount_shouldReturn404_whenNotFound() throws Exception {
        when(accountsService.fetchAccount("0000000000"))
            .thenThrow(new ResourceNotFoundException("Account", "mobileNumber", "0000000000"));

        mockMvc.perform(get("/api/fetch")
                .param("mobileNumber", "0000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
```


---

## 7️⃣ Code Coverage with JaCoCo

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum> <!-- 80% minimum -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```


Run with:

```shell script
mvn clean verify
# Report generated at: target/site/jacoco/index.html
```


---

## 📐 Industry Patterns Summary

| Pattern                      | Purpose                               | Annotation/Tool               |
|------------------------------|---------------------------------------|-------------------------------|
| **Arrange-Act-Assert (AAA)** | Clear test structure                  | Convention                    |
| **Given-When-Then**          | BDD-style readability                 | Convention                    |
| **Test Slicing**             | Load only needed context              | `@WebMvcTest`, `@DataJpaTest` |
| **Test Doubles**             | Replace real dependencies             | `@MockBean`, `@Mock`          |
| **Test Containers**          | Real infra in tests                   | `@Testcontainers`             |
| **Contract Testing**         | Verify API contracts between services | Spring Cloud Contract         |
| **Property-based tests**     | Edge case discovery                   | `jqwik` library               |
| **Builder pattern**          | Readable test data setup              | Lombok `@Builder` on DTOs     |
| **`@Nested`**                | Group related test scenarios          | JUnit 5                       |

---

## ✅ Recommended Coverage Targets

| Layer                       | Target    |
|-----------------------------|-----------|
| Service (business logic)    | **≥ 80%** |
| Controller (web layer)      | **≥ 70%** |
| Repository (custom queries) | **≥ 60%** |
| Exception handlers          | **100%**  |
| Utility/Mappers             | **≥ 90%** |
| Gateway filters             | **≥ 80%** |

---

## 🏷️ Test Annotations Reference

A complete reference of all annotations commonly used in Spring Boot test suites.

---

### 🔵 JUnit 5 (Jupiter) Annotations

| Annotation           | Description                                                                                  |
|----------------------|----------------------------------------------------------------------------------------------|
| `@Test`              | Marks a method as a test case.                                                               |
| `@ParameterizedTest` | Runs the same test multiple times with different arguments.                                  |
| `@ValueSource`       | Provides a single array of literal values to a `@ParameterizedTest`.                         |
| `@CsvSource`         | Provides comma-separated values as arguments to a `@ParameterizedTest`.                      |
| `@MethodSource`      | Provides arguments from a factory method to a `@ParameterizedTest`.                          |
| `@EnumSource`        | Provides enum constants as arguments to a `@ParameterizedTest`.                              |
| `@RepeatedTest`      | Repeats a test a specified number of times.                                                  |
| `@BeforeEach`        | Executes the annotated method before **each** test in the class.                             |
| `@AfterEach`         | Executes the annotated method after **each** test in the class.                              |
| `@BeforeAll`         | Executes the annotated method **once** before all tests in the class (must be `static`).     |
| `@AfterAll`          | Executes the annotated method **once** after all tests in the class (must be `static`).      |
| `@Nested`            | Declares a nested, non-static inner test class for logical grouping of related tests.        |
| `@DisplayName`       | Declares a custom display name for a test class or method (improves readability in reports). |
| `@Tag`               | Declares a tag for filtering tests (e.g., `@Tag("unit")`, `@Tag("integration")`).            |
| `@Disabled`          | Disables a test class or method. Accepts an optional reason string.                          |
| `@Timeout`           | Fails a test if its execution exceeds a given duration.                                      |
| `@TempDir`           | Injects a temporary directory (`Path` or `File`) that is cleaned up after the test.          |
| `@ExtendWith`        | Registers extensions for a test class (e.g., `MockitoExtension.class`).                      |
| `@Order`             | Controls execution order of test methods (used with `@TestMethodOrder`).                     |
| `@TestMethodOrder`   | Configures the ordering strategy for test methods in a class.                                |

---

### 🟠 Mockito Annotations

> Require `@ExtendWith(MockitoExtension.class)` on the class, or `MockitoAnnotations.openMocks(this)` in `@BeforeEach`.

| Annotation         | Description                                                                                          |
|--------------------|------------------------------------------------------------------------------------------------------|
| `@Mock`            | Creates a full Mockito mock of the annotated type. All methods return default values unless stubbed. |
| `@Spy`             | Creates a Mockito spy that wraps a real object. Real methods are called unless stubbed.              |
| `@InjectMocks`     | Creates an instance of the annotated class and injects all `@Mock`/`@Spy` fields into it.            |
| `@Captor`          | Creates an `ArgumentCaptor` for capturing method arguments passed to mocks for later assertions.     |
| `@MockitoSettings` | Configures Mockito settings per class (e.g., strictness level).                                      |

---

### 🟢 Spring Boot Test Slices

These annotations load a **partial** Spring application context, making tests faster and more focused.

| Annotation                 | What It Loads                                                       | Typical Use                                        |
|----------------------------|---------------------------------------------------------------------|----------------------------------------------------|
| `@SpringBootTest`          | Full application context.                                           | End-to-end integration tests.                      |
| `@WebMvcTest(X.class)`     | Web layer only (controllers, filters, `@ControllerAdvice`).         | Controller and exception handler tests.            |
| `@DataJpaTest`             | JPA layer only (repositories, `EntityManager`). Auto-configures H2. | Repository / data access tests.                    |
| `@DataMongoTest`           | MongoDB layer only.                                                 | MongoDB repository tests.                          |
| `@RestClientTest(X.class)` | REST client components only. Configures `MockRestServiceServer`.    | `RestTemplate` / `RestClient` tests.               |
| `@JsonTest`                | JSON serialization/deserialization only (`ObjectMapper`).           | DTO serialization tests.                           |
| `@WebFluxTest(X.class)`    | Reactive web layer only. Configures `WebTestClient`.                | Reactive controller tests (e.g., Gateway filters). |

---

### 🟣 Spring Boot Test Utility Annotations

| Annotation                   | Description                                                                                                                                                    |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@MockBean`                  | Creates a Mockito mock and registers it as a Spring bean in the application context, replacing any existing bean of the same type. Essential in `@WebMvcTest`. |
| `@SpyBean`                   | Wraps an existing Spring bean with a Mockito spy.                                                                                                              |
| `@Autowired`                 | Injects Spring-managed beans into the test class.                                                                                                              |
| `@Value`                     | Injects a property value from `application.properties`/`application.yml` into a test field.                                                                    |
| `@ActiveProfiles`            | Activates specific Spring profiles for the test (e.g., `@ActiveProfiles("test")`).                                                                             |
| `@TestPropertySource`        | Overrides specific properties for the test (e.g., datasource URL, feature flags).                                                                              |
| `@DynamicPropertySource`     | Registers dynamic properties at runtime (e.g., Testcontainers JDBC URL).                                                                                       |
| `@AutoConfigureMockMvc`      | Auto-configures `MockMvc` when used with `@SpringBootTest` (not needed with `@WebMvcTest`).                                                                    |
| `@AutoConfigureTestDatabase` | Overrides the datasource configuration for tests (e.g., replace with H2).                                                                                      |
| `@Transactional`             | Wraps each test in a transaction that is **rolled back** after the test, keeping the DB clean.                                                                 |
| `@Rollback`                  | Controls whether the transaction started by `@Transactional` should be rolled back (default: `true`).                                                          |
| `@Sql`                       | Executes SQL scripts before/after a test method or class (for setting up or cleaning test data).                                                               |
| `@SqlGroup`                  | Container annotation for multiple `@Sql` declarations.                                                                                                         |
| `@Import`                    | Imports additional `@Configuration` classes into the test context (useful in sliced tests).                                                                    |

---

### 🔴 Testcontainers Annotations

> Require the `org.testcontainers:junit-jupiter` dependency.

| Annotation        | Description                                                                                                                                                                 |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@Testcontainers` | Activates automatic lifecycle management for containers declared with `@Container` in the test class.                                                                       |
| `@Container`      | Marks a `GenericContainer` field (e.g., `MySQLContainer`) for automatic start/stop by `@Testcontainers`. Use `static` for a shared container across all tests in the class. |

---

### ⚪ Additional Useful Annotations

| Annotation                         | Library                        | Description                                                                                 |
|------------------------------------|--------------------------------|---------------------------------------------------------------------------------------------|
| `@AutoConfigureWireMock(port = 0)` | Spring Cloud Contract WireMock | Starts a WireMock server on a random port and injects `wiremock.server.port` as a property. |
| `@WithMockUser`                    | Spring Security Test           | Runs the test with a mock authenticated user (configurable roles, username).                |
| `@WithAnonymousUser`               | Spring Security Test           | Runs the test as an anonymous (unauthenticated) user.                                       |
| `@WithUserDetails`                 | Spring Security Test           | Runs the test with a specific user (configurable roles,                                     |


---

### 💡 Annotation Combinations Cheat Sheet

| Test Scenario                | Key Annotations                                                                                                |
|------------------------------|----------------------------------------------------------------------------------------------------------------|
| Pure unit test (no Spring)   | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, `@Test`                                        |
| Controller test              | `@WebMvcTest(XController.class)`, `@MockBean`, `@Autowired MockMvc`                                            |
| Repository test (H2)         | `@DataJpaTest`, `@AutoConfigureTestDatabase`, `@Autowired TestEntityManager`                                   |
| Integration test (real DB)   | `@SpringBootTest`, `@Testcontainers`, `@Container`, `@DynamicPropertySource`                                   |
| Integration test (mock HTTP) | `@SpringBootTest`, `@AutoConfigureWireMock(port=0)`, `@TestPropertySource`                                     |
| JSON serialization test      | `@JsonTest`, `@Autowired JacksonTester`                                                                        |
| Profile-specific test        | `@ActiveProfiles("test")`, `@TestPropertySource`                                                               |
| Parameterized test           | `@ParameterizedTest`, `@CsvSource` / `@ValueSource` / `@MethodSource`                                          |
| Grouped test scenarios       | `@Nested`, `@DisplayName`                                                                                      |
| DB state management          | `@Transactional`, `@Sql(scripts = "...")`, `@Rollback`                                                         |
| Spring Security test         | `@WithMockUser`, `@WithAnonymousUser`                                                                          |
| WireMock test                | `@AutoConfigureWireMock(port=0)`, `@TestPropertySource` with Feign client URLs pointing to WireMock server.    |
| Contract test                | `@SpringBootTest`, Spring Cloud Contract dependencies, and contract stubs in `src/test/resources/contracts`.   |
| Property-based test          | `@ParameterizedTest`, `@MethodSource` with a method that generates random inputs using a library like `jqwik`. |
| Builder pattern test         | `@Test`, with test data setup using Lombok `@Builder` on DTOs for readability and maintainability.             |
| Spring Boot Test Utilities   | `@MockBean`, `@ActiveProfiles`, `@DynamicPropertySource`, `@Sql`, etc., depending on the test scenario.        |

```
- **🔵 JUnit 5** — All core testing lifecycle, parameterization, and structural annotations
- **🟠 Mockito** — `@Mock`, `@Spy`, `@InjectMocks`, `@Captor`
- **🟢 Spring Boot Test Slices** — `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`, etc., with a clear description of *what each loads*
- **🟣 Spring Boot Test Utilities** — `@MockBean`, `@ActiveProfiles`, `@DynamicPropertySource`, `@Sql`, etc.
- **🔴 Testcontainers** — `@Testcontainers` and `@Container`
- **⚪ Additional** — WireMock and Spring Security test annotations
- **💡 Cheat Sheet** — Quick-reference table mapping common test scenarios to the right annotation combinations