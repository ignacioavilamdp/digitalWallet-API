package org.alkemy.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alkemy.wallet.dto.TransactionSendMoneyDto;
import org.alkemy.wallet.model.*;
import org.alkemy.wallet.repository.IAccountRepository;
import org.alkemy.wallet.repository.ITransactionRepository;
import org.alkemy.wallet.repository.IUserRepository;
import org.alkemy.wallet.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionsSendArsTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ITransactionRepository transactionRepository;
    @MockBean
    private IAccountRepository accountRepository;
    @MockBean
    private IUserRepository userRepositoryMock;
    @Autowired
    private ObjectMapper jsonMapper;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    private String userToken;
    private Account destinyAccount;
    private TransactionSendMoneyDto transactionSendMoneyDto;


    @BeforeEach
    public void setUp(){
        Role userRole = new Role(1L, RoleName.USER, "USER Role", new Date(), new Date());
        Role adminRole = new Role(2L, RoleName.ADMIN, "ADMIN Role", new Date(), new Date());
        User user = new User("UserFN", "UserLN", "userEmail@email.com", "1234", userRole);
        User admin = new User("AdminFN", "AdminLN", "adminEmail@email.com", "5678", adminRole);
        user.setId(1L);
        admin.setId(2L);
        when(userRepositoryMock.findByEmail(user.getEmail())).thenReturn(user);
        when(userRepositoryMock.findByEmail(admin.getEmail())).thenReturn(admin);
        UserDetails loggedUserDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), List.of(new SimpleGrantedAuthority(RoleName.USER.name())));
        userToken = jwtTokenUtil.generateToken(loggedUserDetails);

        Account account = new Account();
        account.setBalance(1000D);
        account.setId(1L);
        account.setUser(user);
        account.setCurrency(Currency.ARS);
        account.setTransactionLimit(2000D);
        account.setCreationDate(new Date());
        account.setSoftDelete(false);
        account.setUpdateDate(new Date());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findByCurrencyAndUser(account.getCurrency(), account.getUser())).thenReturn(account);

        destinyAccount = new Account();
        destinyAccount.setBalance(10300D);
        destinyAccount.setId(2L);
        destinyAccount.setUser(admin);
        destinyAccount.setCurrency(Currency.ARS);
        destinyAccount.setTransactionLimit(5000D);
        destinyAccount.setCreationDate(new Date());
        destinyAccount.setSoftDelete(false);
        destinyAccount.setUpdateDate(new Date());
        when(accountRepository.findById(destinyAccount.getId())).thenReturn(Optional.of(destinyAccount));

        transactionSendMoneyDto = new TransactionSendMoneyDto();
        transactionSendMoneyDto.setAmount(550D);
        transactionSendMoneyDto.setDescription("SendArs");
        transactionSendMoneyDto.setDestinationAccountId(2L);

        jsonMapper = new ObjectMapper();
    }

    @Test
    void post_TokenProvided_OkResponse() throws Exception {
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(550D))
                .andExpect(jsonPath("$.accountId").value(1L))
                .andExpect(jsonPath("$.description").value("SendArs"))
                .andExpect(jsonPath("$.typeTransaction").value(TypeTransaction.PAYMENT
                        .name()));
    }

    @Test
    void post_InvalidAmountProvided_BadRequestResponse() throws Exception {
        transactionSendMoneyDto.setAmount(-100D);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Amount must be greater than 0"));
    }

    @Test
    void post_DestinationAccountNull_NotFoundResponse() throws Exception {
        transactionSendMoneyDto.setDestinationAccountId(null);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Not found"));
    }

    @Test
    void post_DestinationAccountEqualsCurrentAccount_BadRequestResponse() throws Exception {
        transactionSendMoneyDto.setDestinationAccountId(1L);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot be the same account"));
    }

    @Test
    void post_DestinationAccountWithDifferentCurrency_BadRequestResponse() throws Exception {
        destinyAccount.setCurrency(Currency.USD);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot be different types of currency"));
    }

    @Test
    void post_AmountAboveTheLimit_BadRequestResponse() throws Exception {
        transactionSendMoneyDto.setAmount(12000D);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Amount must be less than the limit"));
    }

    @Test
    void post_InsufficientBalance_BadRequestResponse() throws Exception {
        transactionSendMoneyDto.setAmount(1500D);
        mockMvc.perform(post("/transactions/sendArs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not enough founds"));
    }

    @Test
    void post_InvalidTokenProvided_UnauthorizedResponse() throws Exception {
        mockMvc.perform(post("/transactions/deposit")
                        .header("Authorization", "Bearer " + userToken + "fail")
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(transactionSendMoneyDto)))
                .andExpect(status().isUnauthorized());
    }
}