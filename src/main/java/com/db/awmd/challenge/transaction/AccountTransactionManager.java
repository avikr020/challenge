package com.db.awmd.challenge.transaction;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * @author This class Handles Transactional Operations
 */
@Slf4j
public class AccountTransactionManager {

    private final AccountsRepository accountsRepository;

    private TransactionInvocationHandler<Account> handler;

    @Getter
    private boolean autoCommit = false;

    @Getter
    private AccountsRepository repoProxy;

    public AccountTransactionManager(AccountsRepository repository) {
        this.accountsRepository = repository;

        handler = new TransactionInvocationHandler<>(accountsRepository);
        repoProxy = (AccountsRepository) Proxy.newProxyInstance(AccountsRepository.class.getClassLoader()
                , new Class[]{AccountsRepository.class}, handler);

    }

    public void doInTransaction(TransactionCallback callback) {
        TransactionContext<Account, Account> context = new TransactionContext<>();
        ThreadLocal<TransactionContext<Account, Account>> localContext = handler.getLocalContext();
        localContext.set(context);
        try {
            callback.process();
            if (autoCommit) {
                commit();
            }
        } catch (Exception e) {
            rollBack();
            throw e;
        }
    }

    public void commit() {
        TransactionContext<Account, Account> localContext = handler.getLocalContext().get();
        Map<Account, Account> savePoints = localContext.getSavePoints();
        // swap save points value to repository
        savePoints.forEach((key, value) -> {
            value.setBalance(key.getBalance());
            value.getSemaphore().release();
            log.info("Lock released from account {}", value.getAccountId());
        });
    }

    public void rollBack() {
        // Destroy Save points within same transactional context
        TransactionContext<Account, Account> localContext = handler.getLocalContext().get();
        //releasing all lock in case of exception
        localContext.getSavePoints().forEach((key, value) -> {
            value.getSemaphore().release();
            log.info("Lock released from account during rollback {}", value.getAccountId());
        });
        localContext.getSavePoints().clear();
    }


}
