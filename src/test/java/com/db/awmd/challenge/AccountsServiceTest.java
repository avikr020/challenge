package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.AmountTransferException;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    AccountsRepository accountsRepository;

    @Before
    public void init() {
        doNothing().when(notificationService).notifyAboutTransfer(any(), any());
        accountsRepository.clearAccounts();
    }


    private void createAccount(String s, int i) {
        Account accountFrom = new Account(s);
        accountFrom.setBalance(new BigDecimal(i));
        this.accountsService.createAccount(accountFrom);
    }

    @Test
    public void addAccount()  {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    public void addAccount_failsOnDuplicateId() {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }

    }

    @Test
    public void amountTransfer_TransactionCommit()  {
        createAccount("Id-341", 1000);
        createAccount("Id-342", 1000);
        this.accountsService.amountTransfer("Id-341", "Id-342", new BigDecimal(1000));
        assertThat(this.accountsService.getAccount("Id-341").getBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(this.accountsService.getAccount("Id-342").getBalance()).isEqualTo(new BigDecimal(2000));
        verify(notificationService, times(2)).notifyAboutTransfer(any(), anyString());
    }

    @Test
    public void amountTransfer_TransactionRollBack()  {
        createAccount("Id-350", 1000);
        createAccount("Id-351", 1000);
        this.accountsService.amountTransfer("Id-350", "Id-351", new BigDecimal(1000));

        try {
            //make transfer when balance insufficient
            this.accountsService.amountTransfer("Id-350", "Id-351", new BigDecimal(500));
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("Insufficient balance in account");
        }
        //Transaction will be rollBack and no account will be updated
        assertThat(this.accountsService.getAccount("Id-350").getBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(this.accountsService.getAccount("Id-351").getBalance()).isEqualTo(new BigDecimal(2000));
        verify(notificationService, times(2)).notifyAboutTransfer(any(), anyString());
    }

    @Test
    public void amountTransfer_TransactionRollBackOnNonExistingAccount()  {
        //make transfer To an Account which do not exist
        String ACCOUNT_NOT_EXIST = ReflectionTestUtils.getField(AccountNotFoundException.class, "ACCOUNT_NOT_EXIST").toString();
        createAccount("Id-360", 1000);
        try {
            this.accountsService.amountTransfer("Id-360", "Id-361", new BigDecimal(500));
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo(String.format(ACCOUNT_NOT_EXIST, "Id-361"));
        }
        //Transaction will be rollBack and no debit will happen
        assertThat(this.accountsService.getAccount("Id-360").getBalance()).isEqualTo(new BigDecimal(1000));
        verify(notificationService, times(0)).notifyAboutTransfer(any(), anyString());
    }

    @Test
    public void when_transfer_amount_zero_assert_Exception_Message() {
        createAccount("Id-341", 1000);
        createAccount("Id-342", 1000);
        try {
            this.accountsService.amountTransfer("Id-341", "Id-342", BigDecimal.ZERO);
            fail("Should have failed as the transfer amount is zero");
        } catch (AmountTransferException e) {
            assertThat(e.getMessage()).isEqualTo(String.format("Invalid transfer amount [%s]", BigDecimal.ZERO));
        }
    }


    @Test
    public void when_fromAccount_toAccount_same_assert_Exception_Message() {
        createAccount("Id-341", 1000);
        try {
            this.accountsService.amountTransfer("Id-341", "Id-341", new BigDecimal(1000));
            fail("Should have failed as the transfer amount is zero");
        } catch (AmountTransferException e) {
            assertThat(e.getMessage()).isEqualTo(String.format("Cannot Transfer to same account : FROM_ACCOUNT [%s] TO_ACCOUNT [%s]", "Id-341", "Id-341"));
        }
    }

    @Test
    public void when_multiple_transfer_between_same_accounts_assert_success() throws Exception {
		/*
		Starting 10 threads to execute  transfer. At the end, the amount from Id-341,Id-342 should be zero,
		and all the amount is transferred to Id-343
		*/
        createAccount("Id-341", 1000);
        createAccount("Id-342", 1000);
        createAccount("Id-343", 0);

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        IntStream.range(0, 10).<Runnable>mapToObj(i -> () -> {
            accountsService.amountTransfer("Id-341", "Id-342", new BigDecimal(100));
            accountsService.amountTransfer("Id-342", "Id-343", new BigDecimal(200));
        }).forEach(executorService::execute);

        TimeUnit.SECONDS.sleep(5);
        assertThat(this.accountsService.getAccount("Id-341").getBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(this.accountsService.getAccount("Id-342").getBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(this.accountsService.getAccount("Id-343").getBalance()).isEqualTo(new BigDecimal(2000));
        verify(notificationService, times(40)).notifyAboutTransfer(any(), anyString());

    }


}
