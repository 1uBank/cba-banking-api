package com.oneutility.cba.bankingapi.virtualaccounts.service;

import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreVirtualAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreVirtualAccountPage;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingVirtualAccounts;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.CreateVirtualAccountRequest;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccount;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccountPage;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccountType;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * The contract's static virtual account endpoints. A virtual account belongs to the client that owns its parent's
 * customer; any other is "not found".
 */
@Service
public class VirtualAccountService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");
    private static final String CURSOR_PREFIX = "v:";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VirtualAccountService.class);

    private final AccountService accounts;
    private final CoreBankingVirtualAccounts virtualAccounts;
    private final CoreBankingCustomers customers;

    public VirtualAccountService(AccountService accounts, CoreBankingVirtualAccounts virtualAccounts,
            CoreBankingCustomers customers) {
        this.accounts = accounts;
        this.virtualAccounts = virtualAccounts;
        this.customers = customers;
    }

    public VirtualAccount create(CreateVirtualAccountRequest request) {
        boolean airtime = request.type() == VirtualAccountType.AIRTIME;
        if (airtime != (request.accountNumber() != null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, airtime
                    ? "An AIRTIME virtual account needs accountNumber: the phone number without its leading 0"
                    : "accountNumber is only for AIRTIME virtual accounts",
                    List.of(new FieldViolation("accountNumber", airtime ? "is required for AIRTIME" : "is only for AIRTIME")));
        }
        if (request.transactionReference() != null) {
            Optional<CoreVirtualAccount> earlier = call(() -> virtualAccounts.findByReference(request.transactionReference()));
            if (earlier.isPresent()) {
                return sameOrConflict(earlier.get(), request);
            }
        }
        accounts.requireOwned(request.parentAccountNumber());
        try {
            return virtualAccount(virtualAccounts.create(request.parentAccountNumber(), request.type().name(),
                    request.label().trim(), request.accountNumber(), request.transactionReference()));
        } catch (CoreBankingException e) {
            throw refused(e);
        }
    }

    public VirtualAccount get(String accountNumber) {
        return virtualAccount(owned(accountNumber));
    }

    public VirtualAccountPage list(String parentAccountNumber, VirtualAccountType type, Integer limit, String cursor) {
        int pageSize = pageSize(limit);
        Long after = afterSavingsId(cursor);
        Long parentSavingsId = parentAccountNumber == null ? null : accounts.requireOwned(parentAccountNumber).savingsId();
        CoreVirtualAccountPage page = call(() -> virtualAccounts.list(CurrentClient.id(), parentSavingsId,
                type == null ? null : type.name(), after, pageSize));
        String next = page.hasMore() && !page.items().isEmpty()
                ? cursor(page.items().get(page.items().size() - 1).savingsId())
                : null;
        // A type this API does not know (for example one added in the back office) is left out rather than failing the page
        return new VirtualAccountPage(page.items().stream().filter(VirtualAccountService::knownType)
                .map(VirtualAccountService::virtualAccount).toList(), next);
    }

    public VirtualAccount rename(String accountNumber, String label) {
        CoreVirtualAccount va = owned(accountNumber);
        try {
            return virtualAccount(virtualAccounts.rename(va.savingsId(), label.trim()));
        } catch (CoreBankingException e) {
            throw refused(e);
        }
    }

    public VirtualAccount close(String accountNumber) {
        CoreVirtualAccount va = owned(accountNumber);
        try {
            return virtualAccount(virtualAccounts.close(va.savingsId()));
        } catch (CoreBankingException e) {
            throw refused(e);
        }
    }

    // Ownership

    private CoreVirtualAccount owned(String accountNumber) {
        Optional<CoreVirtualAccount> va = call(() -> virtualAccounts.findByNumber(accountNumber));
        if (va.isEmpty() || !knownType(va.get()) || !ownedByClient(va.get())) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No such virtual account for this client");
        }
        return va.get();
    }

    private boolean ownedByClient(CoreVirtualAccount va) {
        Optional<String> owner = call(() -> customers.owner(va.clientId()));
        return owner.isPresent() && owner.get().equals(CurrentClient.id());
    }

    private VirtualAccount sameOrConflict(CoreVirtualAccount earlier, CreateVirtualAccountRequest request) {
        if (!earlier.parentAccountNumber().equals(request.parentAccountNumber())
                || !request.type().name().equals(earlier.type()) || !ownedByClient(earlier)) {
            throw new ApiException(ErrorCode.REFERENCE_REUSED,
                    "transactionReference " + request.transactionReference() + " was already used for another request");
        }
        return virtualAccount(earlier);
    }

    // CBA-CORE refusals as the contract's errors

    private static ApiException refused(CoreBankingException e) {
        if (has(e, "virtualaccount.type.unavailable")) {
            return new ApiException(ErrorCode.VIRTUAL_ACCOUNT_TYPE_UNAVAILABLE,
                    "The bank has not set up a product for this type of virtual account yet");
        }
        if (has(e, "duplicate.accountNo") || has(e, "duplicate.accountno")) {
            return new ApiException(ErrorCode.ACCOUNT_NUMBER_TAKEN, "This account number is already in use");
        }
        if (has(e, "virtualaccount.not.empty")) {
            return new ApiException(ErrorCode.ACCOUNT_NOT_EMPTY, "The virtual account still holds money; move it to the parent first");
        }
        if (has(e, "virtualaccount.restricted")) {
            return new ApiException(ErrorCode.ACCOUNT_RESTRICTED, "The virtual account has a hold or block in place");
        }
        if (has(e, "virtualaccount.closed")) {
            return new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "The virtual account is closed");
        }
        if (has(e, "parent.not.active")) {
            return new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE, "The parent account is not active");
        }
        if (has(e, "parent.is.virtual")) {
            return new ApiException(ErrorCode.VALIDATION_FAILED, "A virtual account cannot be the parent of another",
                    List.of(new FieldViolation("parentAccountNumber", "is a virtual account")));
        }
        return AccountService.upstream(e);
    }

    private static boolean has(CoreBankingException e, String part) {
        return e.codes().stream().anyMatch(code -> code.contains(part));
    }

    // Mapping and paging

    private static boolean knownType(CoreVirtualAccount v) {
        if (v.type() != null && java.util.Arrays.stream(VirtualAccountType.values()).anyMatch(t -> t.name().equals(v.type()))) {
            return true;
        }
        LOG.warn("Virtual account {} has type {}, which the contract does not know; it is not shown", v.accountNumber(), v.type());
        return false;
    }

    private static VirtualAccount virtualAccount(CoreVirtualAccount v) {
        return new VirtualAccount(v.accountNumber(), v.name(), v.label(), v.parentAccountNumber(),
                VirtualAccountType.valueOf(v.type()), v.status(), local(v.createdOn()), local(v.updatedOn()));
    }

    private static OffsetDateTime local(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(BANK_ZONE).toOffsetDateTime();
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString((CURSOR_PREFIX + savingsId).getBytes(StandardCharsets.UTF_8));
    }

    static Long afterSavingsId(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith(CURSOR_PREFIX)) {
                return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
            }
        } catch (IllegalArgumentException e) {
            // falls through to the error below
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor is not valid", List.of(new FieldViolation("cursor", "is not valid")));
    }

    private static <T> T call(Supplier<T> coreBankingCall) {
        try {
            return coreBankingCall.get();
        } catch (CoreBankingException e) {
            throw AccountService.upstream(e);
        }
    }
}
