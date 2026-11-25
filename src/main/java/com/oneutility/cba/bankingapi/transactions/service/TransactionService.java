package com.oneutility.cba.bankingapi.transactions.service;

import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransaction;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransactionPage;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransferSides;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingTransactions;
import com.oneutility.cba.bankingapi.transactions.dto.Transaction;
import com.oneutility.cba.bankingapi.transactions.dto.TransactionPage;
import com.oneutility.cba.bankingapi.transactions.dto.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** The contract's transaction history and look-ups, on the client's customers' accounts only. */
@Service
public class TransactionService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_DAYS = 30;
    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");
    private static final String CURSOR_PREFIX = "t:";
    private static final String INSTITUTION_CODE = "090605";
    private static final Set<String> VIRTUAL_ACCOUNT_TYPES = Set.of("SMART_TRANSFER", "AIRTIME", "CASHIER_COLLECTION",
            "MERCHANT_COLLECTION", "ELECTRICITY", "CABLE_TV");

    private final AccountService accounts;
    private final CoreBankingTransactions transactions;
    private final CoreBankingCustomers customers;

    public TransactionService(AccountService accounts, CoreBankingTransactions transactions, CoreBankingCustomers customers) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.customers = customers;
    }

    /** Newest first; both dates included; neither date means the last 30 days. */
    public TransactionPage history(String accountNumber, LocalDate fromDate, LocalDate toDate, TransactionType type, Integer limit,
            String cursor) {
        int pageSize = pageSize(limit);
        Long before = beforeTransactionId(cursor);
        LocalDate to = toDate != null ? toDate : fromDate != null ? fromDate.plusDays(DEFAULT_DAYS - 1L) : LocalDate.now(BANK_ZONE);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "fromDate is after toDate",
                    List.of(new FieldViolation("fromDate", "must not be after toDate")));
        }
        CoreAccount account = accounts.requireOwned(accountNumber);
        CoreTransactionPage page = call(() -> transactions.list(account.savingsId(), from, to,
                type == null ? null : type == TransactionType.CREDIT, before, pageSize));
        String next = page.hasMore() && !page.items().isEmpty()
                ? cursor(page.items().get(page.items().size() - 1).transactionId())
                : null;
        return new TransactionPage(page.items().stream().map(TransactionService::transaction).toList(), next);
    }

    public Transaction get(String accountNumber, String transactionId) {
        CoreAccount account = accounts.requireOwned(accountNumber);
        long id;
        try {
            id = Long.parseLong(transactionId);
        } catch (NumberFormatException e) {
            throw notFound();
        }
        return call(() -> transactions.get(account.savingsId(), id)).map(TransactionService::transaction).orElseThrow(TransactionService::notFound);
    }

    /** By the client's reference, or by NIP session ID; only transactions on the client's customers' accounts. */
    public TransactionPage find(String transactionReference, String sessionId) {
        if ((transactionReference == null) == (sessionId == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Send transactionReference or sessionId, not both",
                    List.of(new FieldViolation("transactionReference", "send transactionReference or sessionId")));
        }
        if (sessionId != null) {
            // NIP transfers are not posted yet; none carries a session ID
            return new TransactionPage(List.of(), null);
        }
        Map<Long, Boolean> owned = new HashMap<>();
        List<Transaction> found = call(() -> transactions.byReference(transactionReference)).stream()
                .filter(t -> owned.computeIfAbsent(t.clientId(), this::ownedByClient))
                .map(TransactionService::transaction).toList();
        return new TransactionPage(found, null);
    }

    private boolean ownedByClient(long clientId) {
        Optional<String> owner = call(() -> customers.owner(clientId));
        return owner.isPresent() && owner.get().equals(CurrentClient.id());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "No such transaction on this account");
    }

    // Mapping

    static Transaction transaction(CoreTransaction t) {
        CoreTransferSides s = t.transfer();
        Transaction.TransferLegs legs = s == null ? null
                : new Transaction.TransferLegs(Long.toString(s.transferId()), s.fromAccountNumber(), Long.toString(s.fromTransactionId()),
                        s.toAccountNumber(), Long.toString(s.toTransactionId()));
        Transaction.Counterparty counterparty = s == null ? null
                : t.credit() ? new Transaction.Counterparty(s.fromName(), s.fromAccountNumber(), INSTITUTION_CODE, null)
                        : new Transaction.Counterparty(s.toName(), s.toAccountNumber(), INSTITUTION_CODE, null);
        return new Transaction(Long.toString(t.transactionId()), t.accountNumber(), t.credit() ? "CREDIT" : "DEBIT", kind(t), money(t.amount()),
                "NGN", money(t.balanceBefore()), money(t.balanceAfter()), s != null ? s.description() : t.note(), t.transactionReference(),
                t.virtualAccountType() != null && VIRTUAL_ACCOUNT_TYPES.contains(t.virtualAccountType()) ? t.virtualAccountType() : null, t.paymentType(), "CONFIRMED",
                t.reversed(), s != null, legs, counterparty, when(t));
    }

    /** What the transaction was, from its Fineract type and whether it belongs to a transfer. */
    static String kind(CoreTransaction t) {
        if (t.reversal()) {
            return "REVERSAL";
        }
        if (t.transfer() != null) {
            return "INTRA_BANK_TRANSFER";
        }
        return switch (t.typeEnum()) {
            case 2 -> t.transactionReference() != null ? "SERVICE_DEBIT" : "OTHER";
            case 3, 17 -> "INTEREST";
            case 4, 5 -> "FEE";
            case 7, 18 -> "CHARGE";
            default -> "OTHER";
        };
    }

    private static OffsetDateTime when(CoreTransaction t) {
        return t.createdOn() != null ? t.createdOn().atZoneSameInstant(BANK_ZONE).toOffsetDateTime()
                : t.date().atStartOfDay(BANK_ZONE).toOffsetDateTime();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    // Paging: newest first; the cursor is the last transaction ID of the page.

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

    static String cursor(long transactionId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((CURSOR_PREFIX + transactionId).getBytes(StandardCharsets.UTF_8));
    }

    static Long beforeTransactionId(String cursor) {
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
