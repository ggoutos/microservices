# Mobile Number Join Strategy - Fix Recommendations

## Current Problem Analysis

### Current Architecture Issues
The system currently uses `mobile_number` as a logical join key across three separate microservices with independent databases:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  accountsdb     │     │   loansdb       │     │   cardsdb       │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ customer        │     │ loans           │     │ cards           │
│ - customer_id   │     │ - loan_id       │     │ - card_id       │
│ - name          │     │ - mobile_number │◄────│ - mobile_number │
│ - email         │     │ - loan_number   │     │ - card_number   │
│ - mobile_number │────►│ - loan_type     │     │ - card_type     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        ▲                       │                       │
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                    JOIN via mobile_number (NO FK CONSTRAINTS)
```

### Critical Problems

1. **No Referential Integrity**: Cannot enforce foreign key constraints across databases
2. **Data Inconsistency Risk**: Mobile number changes require updates in 3 services
3. **No Uniqueness Guarantee**: Nothing prevents duplicate mobile numbers in loans/cards tables
4. **Query Complexity**: Aggregation requires distributed queries via Feign clients
5. **Orphaned Records**: Deleting a customer doesn't cascade to loans/cards
6. **Index Inefficiency**: String-based joins are slower than integer joins

---

## Recommended Solutions

### Solution 1: Customer ID Propagation (RECOMMENDED) ⭐⭐⭐⭐⭐

**Strategy**: Use `customer_id` as the universal join key instead of `mobile_number`

#### Benefits
- Integer-based joins (faster than string)
- Clear ownership: Customer entity owns the relationship
- Enables proper indexing and query optimization
- Aligns with domain-driven design principles

#### Implementation Steps

##### Step 1: Update Loans Database Schema
Create migration file: `/workspace/loans/src/main/resources/db/migration/V2__add_customer_id.sql`

```sql
-- Add customer_id column
ALTER TABLE loans 
ADD COLUMN customer_id BIGINT NULL AFTER loan_id;

-- Create index for performance
CREATE INDEX idx_loans_customer_id ON loans(customer_id);

-- Optional: Add foreign key if using shared database (not recommended for microservices)
-- ALTER TABLE loans ADD CONSTRAINT fk_loans_customer 
-- FOREIGN KEY (customer_id) REFERENCES customer(customer_id);

-- Keep mobile_number for backward compatibility during transition
-- Mark as deprecated in documentation
```

##### Step 2: Update Cards Database Schema
Create migration file: `/workspace/cards/src/main/resources/db/migration/V2__add_customer_id.sql`

```sql
-- Add customer_id column
ALTER TABLE cards 
ADD COLUMN customer_id BIGINT NULL AFTER card_id;

-- Create index for performance
CREATE INDEX idx_cards_customer_id ON cards(customer_id);

-- Keep mobile_number for backward compatibility during transition
```

##### Step 3: Update Loans Entity
Modify: `/workspace/loans/src/main/java/com/ggoutos/loans/entity/Loans.java`

```java
@Entity
@Table(name = "loans")
@Getter @Setter @ToString @RequiredArgsConstructor
public class Loans extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loanId;

    @Column(name = "customer_id")
    private Long customerId;  // NEW: Primary join key
    
    @Column(name = "mobile_number")
    @Deprecated  // Mark for future removal
    private String mobileNumber;  // Keep temporarily for backward compatibility

    // ... rest of fields
}
```

##### Step 4: Update Cards Entity
Modify: `/workspace/cards/src/main/java/com/ggoutos/cards/entity/Cards.java`

```java
@Entity
@Table(name = "cards")
@Getter @Setter @ToString @RequiredArgsConstructor
public class Cards extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "customer_id")
    private Long customerId;  // NEW: Primary join key

    @Column(name = "mobile_number")
    @Deprecated  // Mark for future removal
    private String mobileNumber;  // Keep temporarily

    // ... rest of fields
}
```

##### Step 5: Update DTOs
Modify: `/workspace/utils/src/main/java/com/ggoutos/utils/dto/LoansDto.java`

```java
@Data
public class LoansDto {
    private Long loanId;
    private Long customerId;  // ADD this field
    private String mobileNumber;  // Keep for backward compat
    private String loanNumber;
    // ... other fields
}
```

Modify: `/workspace/utils/src/main/java/com/ggoutos/utils/dto/CardsDto.java`

```java
@Data
public class CardsDto {
    private Long cardId;
    private Long customerId;  // ADD this field
    private String mobileNumber;  // Keep for backward compat
    private String cardNumber;
    // ... other fields
}
```

##### Step 6: Update Repository Methods
Modify: `/workspace/loans/src/main/java/com/ggoutos/loans/repository/LoansRepository.java`

```java
public interface LoansRepository extends JpaRepository<Loans, Long> {
    
    // NEW: Primary lookup method
    Optional<Loans> findByCustomerId(Long customerId);
    
    // OLD: Keep for transition period
    @Deprecated
    Optional<Loans> findByMobileNumber(String mobileNumber);
    
    // Support both during transition
    default Optional<Loans> findByCustomerIdOrMobileNumber(Long customerId, String mobileNumber) {
        return findByCustomerId(customerId)
            .or(() -> findByMobileNumber(mobileNumber));
    }
}
```

Modify: `/workspace/cards/src/main/java/com/ggoutos/cards/repository/CardsRepository.java`

```java
public interface CardsRepository extends JpaRepository<Cards, Long> {
    
    // NEW: Primary lookup method
    List<Cards> findByCustomerId(Long customerId);
    
    // OLD: Keep for transition period
    @Deprecated
    List<Cards> findByMobileNumber(String mobileNumber);
}
```

##### Step 7: Update Feign Client Interfaces
Modify: `/workspace/accounts/src/main/java/com/ggoutos/accounts/service/client/LoansFeignClient.java`

```java
@FeignClient(name = "loans", fallback = LoansFallback.class)
public interface LoansFeignClient {

    @GetMapping(value = "/api/fetch", consumes = "application/json")
    LoansDto fetchLoanDetails(
        @RequestHeader("eazybank-correlation-id") String correlationId, 
        @RequestParam Long customerId  // CHANGE from mobileNumber to customerId
    );
    
    // Optional: Add new endpoint, keep old one during transition
    @GetMapping(value = "/api/fetch-by-mobile", consumes = "application/json")
    @Deprecated
    LoansDto fetchLoanDetailsByMobile(
        @RequestHeader("eazybank-correlation-id") String correlationId, 
        @RequestParam String mobileNumber
    );
}
```

Modify: `/workspace/accounts/src/main/java/com/ggoutos/accounts/service/client/CardsFeignClient.java`

```java
@FeignClient(name = "cards", fallback = CardsFallback.class)
public interface CardsFeignClient {

    @GetMapping(value = "/api/fetch", consumes = "application/json")
    CardsDto fetchCardDetails(
        @RequestHeader("eazybank-correlation-id") String correlationId, 
        @RequestParam Long customerId  // CHANGE from mobileNumber to customerId
    );
}
```

##### Step 8: Update Service Layer
Modify: `/workspace/accounts/src/main/java/com/ggoutos/accounts/service/impl/CustomersServiceImpl.java`

```java
@Service
public class CustomersServiceImpl implements ICustomersService {
    
    // ... existing code
    
    public CustomerDto fetchCustomerDetails(String mobileNumber) {
        // Find customer by mobile number
        Customer customer = customerRepository.findByMobileNumber(mobileNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        
        // Use customerId for downstream calls
        Long customerId = customer.getCustomerId();
        
        // Fetch loans using customerId
        LoansDto loansDto = loansFeignClient.fetchLoanDetails(correlationId, customerId);
        
        // Fetch cards using customerId
        CardsDto cardsDto = cardsFeignClient.fetchCardDetails(correlationId, customerId);
        
        // Map and return
        return mapToDto(customer, loansDto, cardsDto);
    }
}
```

##### Step 9: Update Controller Layer
Modify: `/workspace/loans/src/main/java/com/ggoutos/loans/controller/LoansController.java`

```java
@RestController
@RequestMapping("/api")
public class LoansController {
    
    @GetMapping("/fetch")
    public ResponseEntity<LoansDto> fetchLoanDetails(
            @RequestHeader("eazybank-correlation-id") String correlationId,
            @RequestParam Long customerId) {  // CHANGE parameter
        return ResponseEntity.ok(loansService.fetchLoanDetails(customerId));
    }
}
```

##### Step 10: Migration Data Strategy
Create data migration script to populate customer_id in loans/cards tables:

```sql
-- For Loans (assuming you have a way to map mobile_number to customer_id)
-- This requires a temporary cross-database lookup or manual mapping
UPDATE loans l
JOIN accountsdb.customer c ON l.mobile_number = c.mobile_number
SET l.customer_id = c.customer_id
WHERE l.customer_id IS NULL;

-- For Cards
UPDATE cards c
JOIN accountsdb.customer cust ON c.mobile_number = cust.mobile_number
SET c.customer_id = cust.customer_id
WHERE c.customer_id IS NULL;

-- Add NOT NULL constraint after migration
ALTER TABLE loans MODIFY COLUMN customer_id BIGINT NOT NULL;
ALTER TABLE cards MODIFY COLUMN customer_id BIGINT NOT NULL;

-- Add unique constraint if business logic allows (one loan/card per customer)
-- ALTER TABLE loans ADD UNIQUE KEY uk_loans_customer (customer_id);
```

---

### Solution 2: Event-Driven Data Synchronization ⭐⭐⭐⭐

**Strategy**: Use domain events to maintain customer data consistency across services

#### Architecture
```
┌──────────────┐      ┌──────────────┐
│   Customer   │─────►│  Event Bus   │
│   Created    │      │  (Kafka/RabbitMQ)
└──────────────┘      └──────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
       ┌──────────┐   ┌──────────┐   ┌──────────┐
       │ Accounts │   │  Loans   │   │  Cards   │
       │ Service  │   │ Service  │   │ Service  │
       └──────────┘   └──────────┘   └──────────┘
```

#### Implementation

##### Step 1: Define Domain Events
Create: `/workspace/utils/src/main/java/com/ggoutos/utils/events/CustomerCreatedEvent.java`

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerCreatedEvent {
    private Long customerId;
    private String name;
    private String email;
    private String mobileNumber;
    private LocalDateTime createdAt;
}
```

##### Step 2: Publish Events from Accounts Service
Modify: `/workspace/accounts/src/main/java/com/ggoutos/accounts/service/impl/AccountsServiceImpl.java`

```java
@Service
public class AccountsServiceImpl implements IAccountsService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Override
    public Accounts save(Accounts accounts, String mobileNumber, String createdBy) {
        // Save account logic
        Accounts savedAccount = accountsRepository.save(accounts);
        
        // Publish event
        CustomerCreatedEvent event = new CustomerCreatedEvent(
            accounts.getCustomerId(),
            // ... other fields
        );
        eventPublisher.publishEvent(event);
        
        return savedAccount;
    }
}
```

##### Step 3: Subscribe in Loans and Cards Services
In Loans Service:

```java
@Component
public class CustomerEventListener {
    
    @Autowired
    private LoansRepository loansRepository;
    
    @EventListener
    public void handleCustomerCreated(CustomerCreatedEvent event) {
        // Initialize default loan record or update customer_id mapping
        // Store customerId -> mobileNumber mapping locally
    }
}
```

#### Benefits
- Loose coupling between services
- Each service maintains its own customer reference
- Supports eventual consistency
- Better scalability

#### Drawbacks
- More complex infrastructure (message broker required)
- Eventual consistency challenges
- Need to handle event failures/retries

---

### Solution 3: API Composition Pattern ⭐⭐⭐

**Strategy**: Centralize customer lookup in Accounts service, aggregate via API calls

#### Implementation
Keep current mobile_number approach but improve it:

##### Step 1: Add Unique Constraints
```sql
-- In loansdb
ALTER TABLE loans ADD UNIQUE KEY uk_loans_mobile (mobile_number);

-- In cardsdb  
ALTER TABLE cards ADD UNIQUE KEY uk_cards_mobile (mobile_number);
```

##### Step 2: Add Validation Annotations
In DTOs and Entities:

```java
@Column(name = "mobile_number")
@Pattern(regexp = "^\\d{10}$", message = "Mobile number must be 10 digits")
@NotBlank(message = "Mobile number cannot be blank")
private String mobileNumber;
```

##### Step 3: Implement Mobile Number Update Cascade
Create a dedicated endpoint to update mobile number across all services:

```java
@PostMapping("/api/customer/update-mobile")
public ResponseEntity<Void> updateMobileNumber(
    @RequestParam Long customerId,
    @RequestParam String newMobileNumber) {
    
    // Update in accounts
    customerRepository.updateMobileNumber(customerId, newMobileNumber);
    
    // Call loans service
    loansFeignClient.updateMobileNumber(customerId, newMobileNumber);
    
    // Call cards service
    cardsFeignClient.updateMobileNumber(customerId, newMobileNumber);
    
    return ResponseEntity.ok().build();
}
```

#### Benefits
- Minimal schema changes
- Easier to implement incrementally
- Works with current architecture

#### Drawbacks
- Still relies on string-based joins
- No referential integrity
- Complex update logic

---

### Solution 4: Shared Customer Database (Anti-Pattern for Microservices) ⭐⭐

**Strategy**: Create a shared customer database that all services reference

```
┌─────────────────────────────────────────────────┐
│           Shared Customer Database              │
│  ┌─────────────────────────────────────────┐    │
│  │  customer                               │    │
│  │  - customer_id (PK)                     │    │
│  │  - name                                 │    │
│  │  - email                                │    │
│  │  - mobile_number                        │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
          ▲              ▲              ▲
          │              │              │
    ┌─────┴────┐   ┌─────┴────┐   ┌─────┴────┐
    │ accounts │   │  loans   │   │  cards   │
    │    db    │   │    db    │   │    db    │
    └──────────┘   └──────────┘   └──────────┘
```

#### Why NOT Recommended
- Violates microservices autonomy principle
- Creates tight coupling
- Single point of failure
- Database becomes bottleneck
- Difficult to scale independently

---

## Comparison Matrix

| Criteria | Solution 1: Customer ID | Solution 2: Event-Driven | Solution 3: API Composition | Solution 4: Shared DB |
|----------|------------------------|-------------------------|----------------------------|----------------------|
| **Referential Integrity** | ✅ Partial | ✅ Yes (eventual) | ❌ No | ✅ Yes |
| **Performance** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **Implementation Effort** | Medium | High | Low | Low |
| **Microservices Alignment** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ |
| **Scalability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **Data Consistency** | Strong | Eventual | Strong | Strong |
| **Complexity** | Low-Medium | High | Low | Low |

---

## Recommended Implementation Plan

### Phase 1: Preparation (Week 1-2)
1. Add `customer_id` columns to loans and cards tables (nullable)
2. Update entity classes to include `customer_id`
3. Add dual lookup methods in repositories (by customerId AND mobileNumber)
4. Deploy without breaking existing functionality

### Phase 2: Migration (Week 3-4)
1. Run data migration to populate `customer_id` from mobile_number mapping
2. Add indexes on `customer_id` columns
3. Update Feign client calls to use `customer_id`
4. Test thoroughly in staging environment

### Phase 3: Cutover (Week 5)
1. Switch primary lookup to `customer_id`
2. Mark mobile_number lookups as deprecated
3. Monitor for issues
4. Update documentation

### Phase 4: Cleanup (Week 6+)
1. Remove mobile_number columns (after sufficient time)
2. Add NOT NULL constraints on customer_id
3. Remove deprecated code
4. Performance optimization

---

## Additional Recommendations

### 1. Add Database Constraints
```sql
-- Ensure data quality
ALTER TABLE loans ADD CONSTRAINT chk_loan_amount CHECK (total_loan >= 0);
ALTER TABLE cards ADD CONSTRAINT chk_card_limit CHECK (total_limit >= 0);

-- Add audit triggers for mobile_number changes
CREATE TRIGGER trg_mobile_update BEFORE UPDATE ON loans
FOR EACH ROW
BEGIN
    IF OLD.mobile_number != NEW.mobile_number THEN
        -- Log change or send notification
    END IF;
END;
```

### 2. Implement Soft Deletes
Instead of hard deletes, use soft delete pattern:

```java
@Entity
public class Loans extends BaseEntity {
    // ... existing fields
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by")
    private String deletedBy;
    
    public void markAsDeleted(String deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
```

### 3. Add Composite Indexes
For common query patterns:

```sql
CREATE INDEX idx_loans_customer_status ON loans(customer_id, loan_type);
CREATE INDEX idx_cards_customer_type ON cards(customer_id, card_type);
```

### 4. Implement Caching
Use Redis or similar to cache customer relationships:

```java
@Service
public class CustomerCacheService {
    
    @Cacheable(value = "customer-loans", key = "#customerId")
    public LoansDto getLoansByCustomerId(Long customerId) {
        return loansFeignClient.fetchLoanDetails(correlationId, customerId);
    }
}
```

---

## Conclusion

**Recommended Approach**: **Solution 1 (Customer ID Propagation)** provides the best balance of:
- Performance (integer joins vs string)
- Maintainability (clear ownership)
- Microservices alignment (loose coupling)
- Implementation feasibility (incremental migration)

This approach maintains service autonomy while enabling efficient queries and better data integrity. The migration can be done incrementally without downtime.
