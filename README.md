# Mockito & Test Doubles — Order Service

![Java](https://img.shields.io/badge/Java-21-orange) ![JUnit5](https://img.shields.io/badge/JUnit-5.12-green) ![Mockito](https://img.shields.io/badge/Mockito-5.24-yellowgreen) ![AssertJ](https://img.shields.io/badge/AssertJ-3.27-blue) ![Maven](https://img.shields.io/badge/Maven-3.9-red) ![Tests](https://img.shields.io/badge/tests-22%20passing-brightgreen)

Unit testing a service layer in isolation — first with **hand-written test doubles**, then with **Mockito**.
Part of my 52-week QA Automation roadmap — **Week 2**.

## The system under test

`OrderService.placeOrder(email, productId, quantity)` runs a small checkout flow:

```
validate input → find product → check stock → calculate price → charge payment
              → decrease stock → save order → send confirmation e-mail
```

Every step except validation is a dependency injected through the constructor, so each one can be replaced by a test double.

```
src/main/java/com/servet/shop/
├── model/       Product, Order, PaymentResult             (records)
├── exception/   ProductNotFound, OutOfStock, PaymentFailed
├── repo/        ProductRepository, OrderRepository, PaymentGateway,
│                NotificationService, IdGenerator          (interfaces)
├── service/     OrderService                              (system under test)
└── PriceCalculator                                        (real class → @Spy example)

src/test/java/com/servet/shop/
├── HandWrittenDoublesTest   5 double types, no framework
└── OrderServiceTest         the same ideas with Mockito
```

## 1 · Test doubles without a framework

| Type | What it does | Example in this repo |
|---|---|---|
| **Dummy** | Only fills a parameter; must never be called | `DummyNotificationService` |
| **Stub** | Returns a canned answer | `StubPaymentGateway` |
| **Spy** | Records calls so the test can inspect them | `SpyNotificationService` |
| **Mock** | Expectations set up front, checked with `verify()` | `MockPaymentGateway` |
| **Fake** | Working but simplified implementation | `FakeProductRepository` (a `Map` instead of a DB) |

Classification from Gerard Meszaros, *xUnit Test Patterns*.

## 2 · Mockito features covered

| Feature | Test |
|---|---|
| `@Mock`, `@InjectMocks` (constructor injection), `MockitoExtension` | whole class |
| `when().thenReturn()`, `thenThrow()`, `thenAnswer()` | `HappyPath`, `Failures` |
| `doThrow()` on a `void` method | notification failure does not break the order |
| `ArgumentCaptor` / `@Captor` | saved order has the right fields |
| `verify`, `times`, `never`, `verifyNoInteractions` | failure scenarios |
| `InOrder` | payment → stock → save → notify |
| `@Spy` + `doReturn()` (partial mock) | `SpyExamples` |
| Argument matchers `any`, `eq`, `anyString`, `anyInt` | throughout |
| BDDMockito `given / willReturn / then().should()` | `BddStyle` |
| Strict stubs (`UnnecessaryStubbingException`) | default with `MockitoExtension` |

Test design: boundary values (`0`, `-1`, stock limit, bulk-discount threshold of 10) with `@ParameterizedTest`, and `@Nested` groups for readable reports.

## 3 · A real dependency conflict I debugged

On **JDK 26**, every interface mock worked but `@Spy PriceCalculator` failed:

```
Java 26 (70) is not supported by the current version of Byte Buddy
which officially supports Java 24 (68)
```

`mvn dependency:tree` showed why: AssertJ pulls in an older **Byte Buddy (1.15.11)**, and Maven's *nearest-wins* rule picked it over the version Mockito needs. Mockito uses Byte Buddy to generate subclasses for concrete classes, which is why only the spy broke.
Fix: pin `byte-buddy` and `byte-buddy-agent` in `<dependencyManagement>`.

I also load Mockito as a `-javaagent` in Surefire. JDK 21+ warns about agents that attach themselves at runtime, so the agent is loaded at startup instead.

## Run

```bash
mvn clean test
```

```
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## What I learned
- Writing doubles by hand first made it clear what Mockito actually generates for me.
- Hiding randomness (`UUID`) behind an `IdGenerator` interface makes code deterministic and testable.
- `verifyNoInteractions` checks for side effects that did *not* happen. For a payment flow, these checks matter as much as the happy path.
- Transitive dependencies can silently override each other — `mvn dependency:tree` is the first thing to run.