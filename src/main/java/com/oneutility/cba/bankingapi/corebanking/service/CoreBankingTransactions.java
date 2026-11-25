package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransaction;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransactionPage;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransferSides;
import com.oneutility.cba.bankingapi.corebanking.fineract.TransactionsResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Transactions that moved money on savings accounts in CBA-CORE. Ownership is checked by the caller. */
@Service
public class CoreBankingTransactions {

    private final FineractApi api;

    CoreBankingTransactions(FineractApi api) {
        this.api = api;
    }

    /**
     * A page of the account's transactions, newest first, from {@code fromDate} to {@code toDate} (both included).
     *
     * @param credit {@code true} for credits only, {@code false} for debits only, {@code null} for both
     * @param beforeTransactionId the last transaction of the previous page, or {@code null}
     */
    public CoreTransactionPage list(long savingsId, LocalDate fromDate, LocalDate toDate, Boolean credit, Long beforeTransactionId,
            int limit) {
        TransactionsResponse page = api.listTransactions(savingsId, fromDate.toString(), toDate.toString(),
                credit == null ? null : credit ? "CREDIT" : "DEBIT", beforeTransactionId, limit);
        return new CoreTransactionPage(page.items().stream().map(CoreBankingTransactions::transaction).toList(), page.hasMore());
    }

    public Optional<CoreTransaction> get(long savingsId, long transactionId) {
        return api.getTransaction(savingsId, transactionId).items().stream().findFirst().map(CoreBankingTransactions::transaction);
    }

    /** Every transaction made with the reference: a debit's, or both sides of a transfer's. */
    public List<CoreTransaction> byReference(String transactionReference) {
        return api.findTransactions(transactionReference).items().stream().map(CoreBankingTransactions::transaction).toList();
    }

    private static CoreTransaction transaction(TransactionsResponse.Item t) {
        TransactionsResponse.Transfer s = t.transfer();
        return new CoreTransaction(t.transactionId(), t.savingsId(), t.accountNo(), t.clientId(), t.typeEnum(),
                "CREDIT".equals(t.entryType()), t.amount(), t.balanceBefore(), t.balanceAfter(), t.transactionDate(), t.createdOn(),
                t.reversed(), t.reversal(), t.transactionReference(), t.paymentType(), t.note(), t.virtualAccountType(),
                s == null ? null : new CoreTransferSides(s.transferId(), s.fromAccountNo(), s.fromTransactionId(), s.fromName(),
                        s.toAccountNo(), s.toTransactionId(), s.toName(), s.description()));
    }
}
