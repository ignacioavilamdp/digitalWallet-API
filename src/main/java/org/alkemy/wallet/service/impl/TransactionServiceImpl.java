package org.alkemy.wallet.service.impl;

import java.util.*;

import org.alkemy.wallet.dto.TransactionDto;
import org.alkemy.wallet.dto.TransactionSendMoneyDto;
import org.alkemy.wallet.exception.BadRequestException;
import org.alkemy.wallet.dto.TransactionRequestDto;
import org.alkemy.wallet.exception.ForbiddenException;
import org.alkemy.wallet.exception.NotFoundException;
import org.alkemy.wallet.mapper.TransactionMapper;
import org.alkemy.wallet.model.*;
import org.alkemy.wallet.model.Currency;
import org.alkemy.wallet.repository.IAccountRepository;
import org.alkemy.wallet.repository.ITransactionRepository;
import org.alkemy.wallet.repository.IUserRepository;
import org.alkemy.wallet.service.IAccountService;
import org.alkemy.wallet.service.ITransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements ITransactionService {

    private final ITransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final IAccountService accountService;
    private final IUserRepository userRepository;
    private final IAccountRepository accountRepository;

    @Autowired
    public TransactionServiceImpl(ITransactionRepository transactionRepository,
                                  TransactionMapper transactionMapper,
                                  IAccountService accountService,
                                  IUserRepository userRepository,
                                  IAccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public TransactionDto save(TransactionRequestDto transactionDto) {

        if (transactionDto.getAmount() <=0)
            throw new BadRequestException("Amount must be greater than 0");

        List<TypeTransaction> validTransactionTypes = Arrays.stream(TypeTransaction.values()).collect(Collectors.toList());
        if (!validTransactionTypes.contains(transactionDto.getTypeTransaction()))
            throw new BadRequestException("Not a valid transaction type");

        if (transactionDto.getDescription() == null)
            throw new BadRequestException("Description is mandatory");

        if (transactionDto.getAccountId() == null)
            throw new BadRequestException("Destination account id is mandatory");

        Optional<Account> account = accountRepository.findById(transactionDto.getAccountId());
        if (account.isEmpty())
            throw new NotFoundException("Account not found");

        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        long loggedUserId = userRepository.findByEmail(loggedUserEmail).getId();
        if (account.get().getUser().getId() != loggedUserId)
            throw new ForbiddenException("Not allow to register transactions in other accounts than yours");

        if (transactionDto.getTypeTransaction() == TypeTransaction.PAYMENT &&
            account.get().getBalance() < transactionDto.getAmount()){
            throw new BadRequestException("Not enough founds");
        }

        Transaction transaction = new Transaction();
        transaction.setTypeTransaction(transactionDto.getTypeTransaction());
        transaction.setTransactionDate(new Date());
        transaction.setAmount(transactionDto.getAmount());
        transaction.setDescript(transactionDto.getDescription());
        transaction.setAccount(account.get());
        transactionRepository.save(transaction);

        Double sum = transaction.getAmount();
        if (transaction.getTypeTransaction() == TypeTransaction.PAYMENT)
            sum *= -1;
        account.get().setBalance(account.get().getBalance() + sum);

        return transactionMapper.transactionToTransactionDto(transaction);
    }

    @Override
    @Transactional
    public TransactionDto findById(Long id) {
        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        long loggedUserId = userRepository.findByEmail(loggedUserEmail).getId();

        Optional<Transaction> transaction = transactionRepository.findById(id);
        if (transaction.isEmpty())
            throw new NotFoundException("No transaction with id: " + id);

        Account account = transaction.get().getAccount();
        if (account.getUser().getId() != loggedUserId)
            throw new ForbiddenException("You are not allowed to view this transaction");

        return transactionMapper.transactionToTransactionDto(transaction.get());
    }

    @Override
    public List<TransactionDto> getAllByUser(long userId) {
        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User loggedUser = userRepository.findByEmail(loggedUserEmail);
        Long loggedUserId = loggedUser.getId();
        Role loggedUserRole = loggedUser.getRole();

        if (loggedUserId != userId && loggedUserRole.getRoleName() != RoleName.ADMIN)
            throw new ForbiddenException("Unable to see other user's transactions");

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty())
            throw new NotFoundException("No user with id: " + userId);

        List<Account> accounts= accountRepository.findAllByUser(user.get());
        List<Transaction> transactions= new ArrayList<>();
        accounts.forEach(acc -> {
            transactions.addAll(transactionRepository.findAllByAccount(acc));
        });

        return transactions.stream().
                map(transaction -> transactionMapper.transactionToTransactionDto(transaction)).
                collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public TransactionDto send(TransactionSendMoneyDto transactionSendMoneyDto, Currency currency) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email);
        Account account = accountRepository.findByCurrencyAndUser(currency, user);
        Account destinationAccount = accountRepository.findById(transactionSendMoneyDto.getDestinationAccountId()).orElse(null);
        if (user == null || account == null || destinationAccount == null){
            throw new NotFoundException("Not found");
        }
        if (account == destinationAccount){
            throw new IllegalArgumentException("Cannot be the same account");
        }
        if (destinationAccount.getCurrency() != currency){
            throw new IllegalArgumentException("Cannot be different types of currency");
        }
        if (transactionSendMoneyDto.getAmount() <= 0){
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (transactionSendMoneyDto.getAmount() > account.getTransactionLimit()){
            throw new IllegalArgumentException("Amount must be less than the limit");
        }

        TransactionRequestDto originTransactionDto = new TransactionRequestDto();
        originTransactionDto.setAmount(transactionSendMoneyDto.getAmount());
        originTransactionDto.setDescription(transactionSendMoneyDto.getDescription());
        originTransactionDto.setAccountId(account.getId());
        originTransactionDto.setTypeTransaction(TypeTransaction.PAYMENT);
        TransactionDto transactionDto = save(originTransactionDto);

        TransactionRequestDto destinyTransactionDto = new TransactionRequestDto();
        destinyTransactionDto.setAmount(transactionSendMoneyDto.getAmount());
        destinyTransactionDto.setDescription(transactionSendMoneyDto.getDescription());
        destinyTransactionDto.setAccountId(account.getId());
        destinyTransactionDto.setTypeTransaction(TypeTransaction.INCOME);
        save(destinyTransactionDto);

        return transactionDto;
    }

	@Override
	@Transactional
	public TransactionDto edit(long id, String description) {
        if (description == null)
            throw new BadRequestException("Description is mandatory");

        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        long loggedUserId = userRepository.findByEmail(loggedUserEmail).getId();

        Optional<Transaction> transaction = transactionRepository.findById(id);
        if (transaction.isEmpty())
            throw new NotFoundException("Transaction not found");

        Account account = transaction.get().getAccount();
        if (account.getUser().getId() != loggedUserId)
            throw new ForbiddenException("You are not allowed to modify this transaction");

        transaction.get().setDescript(description);
        return transactionMapper.transactionToTransactionDto(transaction.get());
	}
}