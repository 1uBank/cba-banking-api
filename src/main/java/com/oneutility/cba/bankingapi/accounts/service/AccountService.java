package com.oneutility.cba.bankingapi.accounts.service;

import com.oneutility.cba.bankingapi.accounts.dto.Account;
import com.oneutility.cba.bankingapi.accounts.dto.AccountPage;
import com.oneutility.cba.bankingapi.accounts.dto.AccountType;
import com.oneutility.cba.bankingapi.accounts.dto.OpenAccountRequest;
import com.oneutility.cba.bankingapi.accounts.dto.Product;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccountStatus;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerIds;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerSearch;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingAccounts;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The contract's Accounts endpoints. A client sees only the accounts of its own customers; anything else, and
 * accounts not yet active, is "not found". Balances are read live from CBA-CORE.
 */
@Service
public class AccountService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    /** Customers with one BVN looked at in an account search. */
    static final int MAX_CUSTOMERS_PER_BVN = 100;
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^\\d{10}$");
    private static final String CURSOR_PREFIX = "a:";

    private final CoreBankingAccounts accounts;
    private final CoreBankingCustomers customers;

    public AccountService(CoreBankingAccounts accounts, CoreBankingCustomers customers) {
        this.accounts = accounts;
        this.customers = customers;
    }

    public AccountPage customerAccounts(long clientId, Integer limit, String cursor) {
        int pageSize = pageSize(limit);
        long after = afterSavingsId(cursor);
        ownedCustomer(clientId);
        Integer kycLevel = call(() -> customers.kycLevel(clientId)).orElse(1);
        List<CoreAccount> all = call(() -> accounts.customerAccounts(clientId)).stream()
                .filter(a -> a.status() != CoreAccountStatus.PENDING).toList();
        return page(all.stream().map(a -> account(a, kycLevel)).toList(), after, pageSize);
    }

    public Account open(long clientId, OpenAccountRequest request) {
        if (request.clientId() != null && request.clientId() != clientId) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "clientId in the body does not match the path",
                    List.of(new FieldViolation("clientId", "must match the path")));
        }
        ownedCustomer(clientId);
        long savingsId;
        try {
            savingsId = accounts.open(clientId, request.productId(), request.transactionReference());
        } catch (CoreBankingException e) {
            throw openRefused(e);
        }
        Integer kycLevel = call(() -> customers.kycLevel(clientId)).orElse(1);
        return account(call(() -> accounts.account(savingsId)), kycLevel);
    }

    public Account get(String accountNumber) {
        CoreAccount account = owned(accountNumber).orElseThrow(AccountService::accountNotFound);
        if (account.status() == CoreAccountStatus.CLOSED) {
            throw accountNotFound();
        }
        return account(account, call(() -> customers.kycLevel(account.clientId())).orElse(1));
    }

    public AccountPage searchByBvn(String bvn, Integer limit, String cursor) {
        int pageSize = pageSize(limit);
        long after = afterSavingsId(cursor);
        CustomerIds matches = call(() -> customers.search(new CustomerSearch(CurrentClient.id(), bvn, null, null, null,
                null, MAX_CUSTOMERS_PER_BVN)));
        List<Account> found = new ArrayList<>();
        for (Long clientId : matches.clientIds()) {
            Integer kycLevel = call(() -> customers.kycLevel(clientId)).orElse(1);
            call(() -> accounts.customerAccounts(clientId)).stream()
                    .filter(a -> a.status() != CoreAccountStatus.PENDING)
                    .forEach(a -> found.add(account(a, kycLevel)));
        }
        return page(found, after, pageSize);
    }

    public List<Product> products() {
        return call(accounts::products).stream().map(p -> new Product(p.id(), p.name(), p.shortName(), p.description()))
                .toList();
    }

    // Ownership

    private void ownedCustomer(long clientId) {
        Optional<String> owner = call(() -> customers.owner(clientId));
        if (owner.isEmpty() || !owner.get().equals(CurrentClient.id())) {
            throw new ApiException(ErrorCode.CUSTOMER_NOT_FOUND, "No customer " + clientId + " for this client");
        }
    }

    /** The account with this number, if it belongs to one of the client's customers and is in use. */
    private Optional<CoreAccount> owned(String accountNumber) {
        if (!ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "accountNumber must be 10 digits",
                    List.of(new FieldViolation("accountNumber", "must be 10 digits")));
        }
        Optional<Long> savingsId = call(() -> accounts.findByNumber(accountNumber));
        if (savingsId.isEmpty()) {
            return Optional.empty();
        }
        CoreAccount account = call(() -> accounts.account(savingsId.get()));
        Optional<String> owner = call(() -> customers.owner(account.clientId()));
        if (owner.isEmpty() || !owner.get().equals(CurrentClient.id()) || account.status() == CoreAccountStatus.PENDING) {
            return Optional.empty();
        }
        return Optional.of(account);
    }

    private static ApiException accountNotFound() {
        return new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No such account for this client");
    }

    // Mapping

    private static Account account(CoreAccount a, Integer kycLevel) {
        return new Account(a.accountNumber(), a.savingsId(), a.clientId(), a.accountName(), a.productId(),
                a.productName(), AccountType.CUSTOMER, a.status().name(), a.currency(), money(a.currentBalance()),
                money(a.availableBalance()), kycLevel);
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    // Paging: accounts in savingsId order; the cursor is the last savingsId of the page.

    private static AccountPage page(List<Account> all, long after, int pageSize) {
        List<Account> rest = all.stream().filter(a -> a.savingsId() > after)
                .sorted(Comparator.comparingLong(Account::savingsId)).toList();
        List<Account> items = rest.stream().limit(pageSize).toList();
        String next = rest.size() > pageSize ? cursor(items.get(items.size() - 1).savingsId()) : null;
        return new AccountPage(items, next);
    }

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

    static String cursor(long savingsId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((CURSOR_PREFIX + savingsId).getBytes(StandardCharsets.UTF_8));
    }

    static long afterSavingsId(String cursor) {
        if (cursor == null) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith(CURSOR_PREFIX)) {
                return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
            }
        } catch (IllegalArgumentException e) {
            // falls through to the error below
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor is not valid",
                List.of(new FieldViolation("cursor", "is not valid")));
    }

    // CBA-CORE errors

    private static ApiException openRefused(CoreBankingException e) {
        if (e.codes().stream().anyMatch(code -> code.contains("savingproduct") || code.contains("savingsproduct"))) {
            return new ApiException(ErrorCode.VALIDATION_FAILED, "No such product",
                    List.of(new FieldViolation("productId", "no such product")));
        }
        if (e.codes().stream().anyMatch(code -> code.contains("client.not.active"))) {
            return new ApiException(ErrorCode.VALIDATION_FAILED, "Accounts can only be opened for an active customer");
        }
        return upstream(e);
    }

    private static <T> T call(Supplier<T> coreBankingCall) {
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
}
