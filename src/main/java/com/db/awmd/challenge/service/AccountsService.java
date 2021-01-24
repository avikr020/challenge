package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AmountTransferException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.transaction.AccountTransactionManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class AccountsService {

    @Getter
    private final AccountsRepository accountsRepository;

    private AccountTransactionManager transactionManager;

    private final NotificationService notificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
        this.accountsRepository = accountsRepository;
        this.transactionManager = new AccountTransactionManager(accountsRepository);
        this.notificationService = notificationService;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    // @Transactional(propagation=Propagation.REQUIRED, readOnly=false, rollbackFor=AmountTransferException.class)
    public void amountTransfer(final String fromAccount,
                               final String toAccount, final BigDecimal transferAmount) throws AmountTransferException {
        validateTransfer(fromAccount, toAccount, transferAmount);
        transactionManager.doInTransaction(() -> {
            this.debit(fromAccount, transferAmount);
            this.credit(toAccount, transferAmount);
        });
        transactionManager.commit();
        notifyAccountHoldersWithTransferDetails(fromAccount, toAccount, transferAmount);
    }

    private void notifyAccountHoldersWithTransferDetails(String fromAccount, String toAccount, BigDecimal transferAmount) {
        try {
            notificationService.notifyAboutTransfer(accountsRepository.getAccount(fromAccount),
                    String.format("Amount: %s debited from account: %s, to account: %s .", transferAmount, fromAccount, toAccount));
            notificationService.notifyAboutTransfer(accountsRepository.getAccount(toAccount),
                    String.format("Amount: %s credited to account: %s, from account: %s .", transferAmount, toAccount, fromAccount));
        } catch (RuntimeException e) {
            log.error("Error sending notifications to account holders for transfer", e);
        }
    }

    private void validateTransfer(String fromAccount, String toAccount, BigDecimal transferAmount) {
        if (fromAccount.compareTo(toAccount) == 0) {
            throw new AmountTransferException(String.format("Cannot Transfer to same account : FROM_ACCOUNT [%s] TO_ACCOUNT [%s]", fromAccount, toAccount));
        }
        if (transferAmount.compareTo(BigDecimal.ZERO) == 0)
            throw new AmountTransferException(String.format("Invalid transfer amount [%s]", transferAmount));
    }

    private Account debit(String accountId, BigDecimal amount) throws AmountTransferException {
        // take repository from transaction manager in order to manage transactions and rollBack.
        //But, This method will only be transactional only if this is called within "transactionManager.doInTransaction()
        // OR method annotated with @AccountTransaction.
        final Account account = transactionManager.getRepoProxy().getAccount(accountId);
        checkForSufficientBalanceInAccount(account.getBalance(), amount);
        BigDecimal bal = account.getBalance().subtract(amount);
        account.setBalance(bal);
        return account;
    }

    private void checkForSufficientBalanceInAccount(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new AmountTransferException("Insufficient balance in account");
        }
    }

    private Account credit(String accountId, BigDecimal amount) throws AmountTransferException {
        // take repository from transaction manager in order to manage transactions and rollBack.
        //But, This method will only be transactional only if this is called within "transactionManager.doInTransaction()
        // OR method annotated with @AccountTransaction.
        final Account account = transactionManager.getRepoProxy().getAccount(accountId);
        BigDecimal bal = account.getBalance().add(amount);
        account.setBalance(bal);
        return account;
    }
}
