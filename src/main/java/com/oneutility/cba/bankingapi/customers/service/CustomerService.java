package com.oneutility.cba.bankingapi.customers.service;

import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAddress;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreCustomer;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreRelatedPerson;
import com.oneutility.cba.bankingapi.corebanking.dto.CreatedCustomer;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerChanges;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerIds;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerSearch;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerType;
import com.oneutility.cba.bankingapi.corebanking.dto.NewCustomer;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingAccounts;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.customers.dto.Address;
import com.oneutility.cba.bankingapi.customers.dto.CreateBusinessRequest;
import com.oneutility.cba.bankingapi.customers.dto.CreateIndividualRequest;
import com.oneutility.cba.bankingapi.customers.dto.CreateCustomerResponse;
import com.oneutility.cba.bankingapi.customers.dto.Customer;
import com.oneutility.cba.bankingapi.customers.dto.CustomerPage;
import com.oneutility.cba.bankingapi.customers.dto.CustomerSearchRequest;
import com.oneutility.cba.bankingapi.customers.dto.KycLevel;
import com.oneutility.cba.bankingapi.customers.dto.RelatedPerson;
import com.oneutility.cba.bankingapi.customers.dto.RiskProfile;
import com.oneutility.cba.bankingapi.customers.dto.SetRiskProfileRequest;
import com.oneutility.cba.bankingapi.customers.dto.TransactionLimit;
import com.oneutility.cba.bankingapi.customers.dto.UpdateBusinessRequest;
import com.oneutility.cba.bankingapi.customers.dto.UpdateIndividualRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Customers of the calling client: creating them with their first account, reading, finding, and setting their KYC
 * level, risk profile and transaction limit. A customer of another client is reported as not found.
 */
@Service
public class CustomerService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    private static final String CURSOR_PREFIX = "c:";

    private final CoreBankingCustomers customers;
    private final CoreBankingAccounts accounts;

    public CustomerService(CoreBankingCustomers customers, CoreBankingAccounts accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    public CreateCustomerResponse createIndividual(CreateIndividualRequest r) {
        NewCustomer customer = new NewCustomer(CurrentClient.id(), CustomerType.PERSON, trim(r.firstName()),
                trim(r.middleName()), trim(r.lastName()), null, r.dateOfBirth(), trim(r.email()), trim(r.mobileNumber()),
                r.bvn(), r.nin(), null, trim(r.tin()), coreAddress(r.address()), r.productId(), r.kycLevel(), r.pep(),
                r.riskRating(), amount(r.transactionLimit()), List.of(), r.transactionReference());
        CreatedCustomer created = create(customer);
        return new CreateCustomerResponse(accountNumber(created), created.savingsId(), created.clientId(),
                CustomerType.PERSON, customer.firstName(), customer.lastName(), null, customer.email(), r.address(),
                r.dateOfBirth(), null, customer.mobileNumber(), r.productId(), false);
    }

    public CreateCustomerResponse createBusiness(CreateBusinessRequest r) {
        NewCustomer customer = new NewCustomer(CurrentClient.id(), CustomerType.BUSINESS, null, null, null,
                trim(r.businessName()), r.registrationDate(), trim(r.email()), trim(r.mobileNumber()), r.bvn(), null,
                trim(r.cacNumber()), trim(r.tin()), coreAddress(r.address()), r.productId(), r.kycLevel(), r.pep(),
                r.riskRating(), amount(r.transactionLimit()), relatedPersons(r.relatedPersons()),
                r.transactionReference());
        CreatedCustomer created = create(customer);
        return new CreateCustomerResponse(accountNumber(created), created.savingsId(), created.clientId(),
                CustomerType.BUSINESS, null, null, customer.fullName(), customer.email(), r.address(), null,
                r.registrationDate(), customer.mobileNumber(), r.productId(), false);
    }

    public Customer get(long clientId) {
        return customer(owned(clientId));
    }

    /** Changes the fields sent, all or nothing, and returns the individual as they now are. */
    public Customer updateIndividual(long clientId, UpdateIndividualRequest r) {
        if (r.isEmpty()) {
            throw nothingToChange();
        }
        update(clientId, CustomerType.PERSON, new CustomerChanges(trim(r.firstName()), trim(r.middleName()),
                trim(r.lastName()), null, r.dateOfBirth(), trim(r.email()), trim(r.mobileNumber()), r.bvn(), r.nin(),
                null, trim(r.tin()), r.address() == null ? null : coreAddress(r.address()), null));
        return customer(owned(clientId));
    }

    /** Changes the fields sent, all or nothing, and returns the business as it now is. */
    public Customer updateBusiness(long clientId, UpdateBusinessRequest r) {
        if (r.isEmpty()) {
            throw nothingToChange();
        }
        update(clientId, CustomerType.BUSINESS, new CustomerChanges(null, null, null, trim(r.businessName()),
                r.registrationDate(), trim(r.email()), trim(r.mobileNumber()), r.bvn(), null, trim(r.cacNumber()),
                trim(r.tin()), r.address() == null ? null : coreAddress(r.address()),
                r.relatedPersons() == null ? null : relatedPersons(r.relatedPersons())));
        return customer(owned(clientId));
    }

    private CreatedCustomer create(NewCustomer customer) {
        if (call(accounts::virtualAccountProductIds).contains(customer.productId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This product is for virtual accounts; use /v1/virtual-accounts",
                    List.of(new FieldViolation("productId", "is a virtual account product")));
        }
        try {
            return customers.create(customer);
        } catch (CoreBankingException e) {
            throw refused(e, identifiers(customer.bvn(), customer.nin(), customer.cacNumber(), customer.tin()));
        }
    }

    private String accountNumber(CreatedCustomer created) {
        return call(() -> accounts.accountNumber(created.savingsId()));
    }

    private void update(long clientId, CustomerType expected, CustomerChanges changes) {
        CoreCustomer existing = owned(clientId);
        if (existing.type() != expected) {
            String use = existing.type() == CustomerType.BUSINESS ? "business-customers" : "individual-customers";
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Customer " + clientId + " is " + (existing.type() == CustomerType.BUSINESS ? "a business" : "an individual")
                            + "; use PUT /v1/" + use + "/" + clientId,
                    List.of(new FieldViolation("clientId", "is not " + (expected == CustomerType.BUSINESS ? "a business"
                            : "an individual"))));
        }
        try {
            customers.update(clientId, existing.type(), withWholeName(changes, existing));
        } catch (CoreBankingException e) {
            throw refused(e, identifiers(changes.bvn(), changes.nin(), changes.cacNumber(), changes.tin()));
        }
    }

    /** Fineract changes a person's name only as a whole: a change to any part sends all of it. */
    private static CustomerChanges withWholeName(CustomerChanges c, CoreCustomer existing) {
        if (existing.type() != CustomerType.PERSON
                || c.firstName() == null && c.middleName() == null && c.lastName() == null) {
            return c;
        }
        return new CustomerChanges(c.firstName() != null ? c.firstName() : existing.firstName(),
                c.middleName() != null ? c.middleName() : existing.middleName(),
                c.lastName() != null ? c.lastName() : existing.lastName(), c.fullName(), c.dateOfBirth(), c.email(),
                c.mobileNumber(), c.bvn(), c.nin(), c.cacNumber(), c.tin(), c.address(), c.relatedPersons());
    }

    private static ApiException nothingToChange() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Send at least one field to change");
    }

    private static BigDecimal amount(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static List<CoreRelatedPerson> relatedPersons(List<RelatedPerson> persons) {
        return persons == null ? List.of() : persons.stream().map(CustomerService::coreRelatedPerson).toList();
    }

    public CustomerPage search(CustomerSearchRequest request, Integer limit, String cursor) {
        if (request.bvn() == null && request.email() == null && request.mobileNumber() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Send at least one of bvn, email, mobileNumber",
                    List.of(new FieldViolation("bvn", "send at least one of bvn, email, mobileNumber")));
        }
        int pageSize = pageSize(limit);
        CustomerIds page = call(() -> customers.search(new CustomerSearch(CurrentClient.id(), request.bvn(), null,
                request.email(), request.mobileNumber(), afterClientId(cursor), pageSize)));
        List<Customer> items = new ArrayList<>();
        for (Long id : page.clientIds()) {
            call(() -> customers.find(id, CurrentClient.id())).map(CustomerService::customer).ifPresent(items::add);
        }
        String next = page.hasMore() && !page.clientIds().isEmpty()
                ? cursor(page.clientIds().get(page.clientIds().size() - 1))
                : null;
        return new CustomerPage(items, next);
    }

    public KycLevel setKycLevel(long clientId, int kycLevel, Long clientIdInBody) {
        if (clientIdInBody != null && clientIdInBody != clientId) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "clientId in the body does not match the path",
                    List.of(new FieldViolation("clientId", "must match the path")));
        }
        owned(clientId);
        call(() -> {
            customers.setKycLevel(clientId, kycLevel);
            return null;
        });
        return new KycLevel(clientId, kycLevel);
    }

    public RiskProfile setRiskProfile(long clientId, SetRiskProfileRequest request) {
        if (request.pep() == null && request.riskRating() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Send pep, riskRating or both",
                    List.of(new FieldViolation("pep", "send pep, riskRating or both")));
        }
        owned(clientId);
        call(() -> {
            customers.setRiskProfile(clientId, request.pep(), request.riskRating());
            return null;
        });
        CoreCustomer now = owned(clientId);
        return new RiskProfile(clientId, now.pep(), now.riskRating());
    }

    public TransactionLimit setTransactionLimit(long clientId, String transactionLimit) {
        owned(clientId);
        BigDecimal limit = new BigDecimal(transactionLimit);
        call(() -> {
            customers.setTransactionLimit(clientId, limit);
            return null;
        });
        return new TransactionLimit(clientId, money(limit));
    }

    // Rules

    private CoreCustomer owned(long clientId) {
        return call(() -> customers.find(clientId, CurrentClient.id())).orElseThrow(
                () -> new ApiException(ErrorCode.CUSTOMER_NOT_FOUND, "No customer " + clientId + " for this client"));
    }

    // CBA-CORE errors

    /** The identifiers a request sends, by their name in error messages. */
    private static Map<String, String> identifiers(String bvn, String nin, String cacNumber, String tin) {
        Map<String, String> sent = new LinkedHashMap<>();
        sent.put("BVN", bvn);
        sent.put("NIN", nin);
        sent.put("CAC registration number", cacNumber);
        sent.put("TIN", tin);
        sent.values().removeIf(java.util.Objects::isNull);
        return sent;
    }

    private static ApiException refused(CoreBankingException e, Map<String, String> sentIdentifiers) {
        if (e.codes().stream().anyMatch(code -> code.startsWith("error.msg.clientIdentifier.identityKey.duplicate")
                || code.startsWith("error.msg.clientIdentifier.type.duplicate"))) {
            return new ApiException(ErrorCode.CUSTOMER_EXISTS,
                    "A customer with this " + duplicateIdentifier(e.getMessage(), sentIdentifiers) + " already exists");
        }
        if (e.codes().stream().anyMatch(code -> code.startsWith("error.msg.client.duplicate.mobileNo"))) {
            return new ApiException(ErrorCode.CUSTOMER_EXISTS, "A customer with this mobile number already exists");
        }
        if (e.codes().stream().anyMatch(code -> code.startsWith("error.msg.client.duplicate.email"))) {
            return new ApiException(ErrorCode.CUSTOMER_EXISTS, "A customer with this email already exists");
        }
        if (e.codes().stream().anyMatch(code -> code.contains("savingproduct") || code.contains("savingsproduct"))) {
            return new ApiException(ErrorCode.VALIDATION_FAILED, "No such product",
                    List.of(new FieldViolation("productId", "no such product")));
        }
        return upstream(e);
    }

    /**
     * Which identifier CBA-CORE found on another customer. Its message quotes the value ("... with value of X already
     * exists"), but not always the type, so the value is matched against what was sent.
     */
    static String duplicateIdentifier(String message, Map<String, String> sentIdentifiers) {
        for (Map.Entry<String, String> sent : sentIdentifiers.entrySet()) {
            if (message.contains("value of " + sent.getValue() + " ")) {
                return sent.getKey();
            }
        }
        return message.contains("type NIN") ? "NIN"
                : message.contains("type CAC") ? "CAC registration number"
                : message.contains("type TIN") ? "TIN"
                : message.contains("type BVN") ? "BVN" : "identifier";
    }

    /** Runs a CBA-CORE call, turning failures into the contract's errors. */
    private static <T> T call(java.util.function.Supplier<T> coreBankingCall) {
        try {
            return coreBankingCall.get();
        } catch (CoreBankingException e) {
            throw upstream(e);
        }
    }

    static ApiException upstream(CoreBankingException e) {
        return switch (e.kind()) {
            case UNAVAILABLE -> new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The core ledger could not be reached");
            case TIMEOUT -> new ApiException(ErrorCode.UPSTREAM_TIMEOUT, "The core ledger did not answer in time");
            case REFUSED -> e.status() == 400
                    ? new ApiException(ErrorCode.VALIDATION_FAILED, "The core ledger refused the request: "
                            + e.getMessage().replaceFirst("^CBA-CORE answered \\d+: ", ""))
                    : new ApiException(ErrorCode.INTERNAL_ERROR, "Unexpected answer from the core ledger");
        };
    }

    // Mapping

    private static Customer customer(CoreCustomer c) {
        boolean business = c.type() == CustomerType.BUSINESS;
        return new Customer(c.clientId(), c.type(), c.firstName(), c.middleName(), c.lastName(), c.fullName(),
                c.email(), c.mobileNumber(), business ? null : c.dateOfBirth(), business ? c.dateOfBirth() : null,
                c.bvn(), c.nin(), c.cacNumber(), c.tin(), address(c.address()), c.kycLevel() == null ? 1 : c.kycLevel(),
                c.pep(), c.riskRating(), c.transactionLimit() == null ? null : money(c.transactionLimit()),
                c.relatedPersons().isEmpty() ? null : c.relatedPersons().stream().map(CustomerService::relatedPerson).toList(),
                c.status());
    }

    private static CoreAddress coreAddress(Address a) {
        return new CoreAddress(trim(a.addressLine1()), trim(a.addressLine2()), trim(a.addressLine3()), trim(a.street()),
                trim(a.townVillage()), trim(a.city()), trim(a.district()), trim(a.state()), trim(a.country()),
                trim(a.postalCode()));
    }

    private static Address address(CoreAddress a) {
        return a == null ? null
                : new Address(a.addressLine1(), a.addressLine2(), a.addressLine3(), a.street(), a.townVillage(),
                        a.city(), a.district(), a.state(), a.country(), a.postalCode());
    }

    private static CoreRelatedPerson coreRelatedPerson(RelatedPerson p) {
        return new CoreRelatedPerson(trim(p.firstName()), trim(p.middleName()), trim(p.lastName()), p.role(), p.bvn(),
                p.nin(), p.dateOfBirth(), p.mobileNumber(), p.email(), trim(p.address()), p.ownershipPercentage());
    }

    private static RelatedPerson relatedPerson(CoreRelatedPerson p) {
        return new RelatedPerson(p.firstName(), p.middleName(), p.lastName(), p.role(), p.bvn(), p.nin(),
                p.dateOfBirth(), p.mobileNumber(), p.email(), p.address(), p.ownershipPercentage());
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Paging

    static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "limit must be between 1 and " + MAX_PAGE_SIZE,
                    List.of(new FieldViolation("limit", "must be between 1 and " + MAX_PAGE_SIZE)));
        }
        return limit;
    }

    static String cursor(long lastClientId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((CURSOR_PREFIX + lastClientId).getBytes(StandardCharsets.UTF_8));
    }

    static Long afterClientId(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith(CURSOR_PREFIX)) {
                return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
            }
        } catch (IllegalArgumentException invalid) {
            // falls through to the error below
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor is not valid",
                List.of(new FieldViolation("cursor", "is not valid; use nextCursor from the previous page")));
    }
}
