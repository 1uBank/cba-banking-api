package com.oneutility.cba.bankingapi.accounts.service;

import com.oneutility.cba.bankingapi.accounts.dto.Account;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreBlock;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreBlockSide;
import com.oneutility.cba.bankingapi.accounts.dto.PlaceBlockRequest;
import com.oneutility.cba.bankingapi.accounts.dto.BlockType;
import com.oneutility.cba.bankingapi.accounts.dto.AccountBlocks;
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
        java.util.Set<Long> vaProducts = call(accounts::virtualAccountProductIds);
        return page(all.stream().map(a -> account(a, kycLevel, vaProducts)).toList(), after, pageSize);
    }

    public Account open(long clientId, OpenAccountRequest request) {
        if (request.clientId() != null && request.clientId() != clientId) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "clientId in the body does not match the path",
                    List.of(new FieldViolation("clientId", "must match the path")));
        }
        ownedCustomer(clientId);
        refuseVirtualAccountProduct(request.productId());
        long savingsId;
        try {
            savingsId = accounts.open(clientId, request.productId(), request.transactionReference());
        } catch (CoreBankingException e) {
            throw openRefused(e);
        }
        Integer kycLevel = call(() -> customers.kycLevel(clientId)).orElse(1);
        return account(call(() -> accounts.account(savingsId)), kycLevel, java.util.Set.of());
    }

    /** Virtual account products are opened only through the virtual account calls. */
    public void refuseVirtualAccountProduct(long productId) {
        if (call(accounts::virtualAccountProductIds).contains(productId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This product is for virtual accounts; use /v1/virtual-accounts",
                    List.of(new FieldViolation("productId", "is a virtual account product")));
        }
    }

    public Account get(String accountNumber) {
        CoreAccount account = owned(accountNumber).orElseThrow(AccountService::accountNotFound);
        if (account.status() == CoreAccountStatus.CLOSED) {
            throw accountNotFound();
        }
        return account(account, call(() -> customers.kycLevel(account.clientId())).orElse(1),
                call(accounts::virtualAccountProductIds));
    }

    /**
     * The account with this number, if it belongs to one of the client's customers and is open; otherwise
     * {@code ACCOUNT_NOT_FOUND}. For other modules acting on an account.
     */
    public CoreAccount requireOwned(String accountNumber) {
        CoreAccount account = owned(accountNumber).orElseThrow(AccountService::accountNotFound);
        if (account.status() == CoreAccountStatus.CLOSED) {
            throw accountNotFound();
        }
        return account;
    }

    // Blocks

    public AccountBlocks blocks(String accountNumber) {
        CoreAccount account = requireOwned(accountNumber);
        return blocks(account, call(() -> accounts.blockSides(account.savingsId())));
    }

    public AccountBlocks block(String accountNumber, PlaceBlockRequest request) {
        CoreAccount account = requireOwned(accountNumber);
        try {
            accounts.block(account.savingsId(), CoreBlock.valueOf(request.type().name()), request.reason().trim());
        } catch (CoreBankingException e) {
            throw refusedOnAccount(e);
        }
        return blocks(accountNumber);
    }

    /** Lifts the client's block of this type; a block the bank placed stays, and then nothing is lifted. */
    public AccountBlocks lift(String accountNumber, BlockType type) {
        CoreAccount account = requireOwned(accountNumber);
        CoreBlock lifting = CoreBlock.valueOf(type.name());
        boolean bankBlock = call(() -> accounts.blockSides(account.savingsId())).stream()
                .anyMatch(side -> !side.placedByClient() && (side.type().equals("DEBIT") ? lifting.debit() : lifting.credit()));
        if (bankBlock) {
            throw new ApiException(ErrorCode.ACCOUNT_RESTRICTED, "The account has a block placed by the bank; only the bank can lift it");
        }
        try {
            accounts.unblock(account.savingsId(), lifting);
        } catch (CoreBankingException e) {
            throw refusedOnAccount(e);
        }
        return blocks(accountNumber);
    }

    private static AccountBlocks blocks(CoreAccount account, List<CoreBlockSide> sides) {
        CoreBlock block = CoreBlock.of(sides.stream().anyMatch(s -> s.type().equals("DEBIT")),
                sides.stream().anyMatch(s -> s.type().equals("CREDIT")));
        return new AccountBlocks(account.accountNumber(), block.name(), sides.stream()
                .map(s -> new AccountBlocks.Side(s.type(), s.placedByClient() ? "CLIENT" : "BANK", s.placedByClient() ? s.reason() : null,
                        s.placedOn() == null ? null : s.placedOn().atZoneSameInstant(java.time.ZoneId.of("Africa/Lagos")).toOffsetDateTime()))
                .toList());
    }

    /**
     * A refusal by CBA-CORE of something done to an account (a block, a hold), as the contract's errors: an account
     * that is not active, or a balance too low, are business rules ({@code 422}).
     */
    public static ApiException refusedOnAccount(CoreBankingException e) {
        if (has(e, "insufficient.account.balance")) {
            return new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "The account's available balance is too low");
        }
        if (has(e, "account.is.not.active") || has(e, "not.active")) {
            return new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "The account is not active");
        }
        // Fineract: error.msg.savings.account.debit.transaction.not.allowed / ...credit.transaction.not.allowed
        if (has(e, "debit.transaction.not.allowed") || has(e, "credit.transaction.not.allowed")) {
            return new ApiException(ErrorCode.ACCOUNT_RESTRICTED,
                    "The core ledger refused: " + e.getMessage().replaceFirst("^CBA-CORE answered \\d+: ", ""));
        }
        if (has(e, "external.id") && (has(e, "duplicate") || has(e, "exists"))) {
            return new ApiException(ErrorCode.REFERENCE_REUSED, "This transactionReference was already used for another request");
        }
        return upstream(e);
    }

    private static boolean has(CoreBankingException e, String part) {
        return e.codes().stream().anyMatch(code -> code.contains(part));
    }

    public AccountPage searchByBvn(String bvn, Integer limit, String cursor) {
        int pageSize = pageSize(limit);
        long after = afterSavingsId(cursor);
        CustomerIds matches = call(() -> customers.search(new CustomerSearch(CurrentClient.id(), bvn, null, null, null,
                null, MAX_CUSTOMERS_PER_BVN)));
        List<Account> found = new ArrayList<>();
        java.util.Set<Long> vaProducts = matches.clientIds().isEmpty() ? java.util.Set.of() : call(accounts::virtualAccountProductIds);
        for (Long clientId : matches.clientIds()) {
            Integer kycLevel = call(() -> customers.kycLevel(clientId)).orElse(1);
            call(() -> accounts.customerAccounts(clientId)).stream()
                    .filter(a -> a.status() != CoreAccountStatus.PENDING)
                    .forEach(a -> found.add(account(a, kycLevel, vaProducts)));
        }
        return page(found, after, pageSize);
    }

    public List<Product> products() {
        return call(accounts::products).stream().filter(p -> !p.forVirtualAccounts())
                .map(p -> new Product(p.id(), p.name(), p.shortName(), p.description())).toList();
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

    /** {@code vaProducts}: the products static virtual accounts are opened on, which make an account {@code STATIC_VA}. */
    private static Account account(CoreAccount a, Integer kycLevel, java.util.Set<Long> vaProducts) {
        return new Account(a.accountNumber(), a.savingsId(), a.clientId(), a.accountName(), a.productId(),
                a.productName(), vaProducts.contains(a.productId()) ? AccountType.STATIC_VA : AccountType.CUSTOMER, a.status().name(), a.currency(), money(a.currentBalance()),
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

    public static ApiException upstream(CoreBankingException e) {
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
