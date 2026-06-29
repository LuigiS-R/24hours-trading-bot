package com.ai_got_this.trading_bot.authentication_management.Service;

import com.ai_got_this.trading_bot.authentication_management.Exceptions.IncorrectPasswordException;
import com.ai_got_this.trading_bot.authentication_management.Exceptions.UserNotFoundException;
import org.springframework.stereotype.Service;
import com.ai_got_this.trading_bot.authentication_management.Repository.AccountRepo;
import com.ai_got_this.trading_bot.authentication_management.Entity.Account;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthenticationService {
    final AccountRepo accountRepo;
    final PasswordEncoder passwordEncoder;
    final TokenManagementService tokenManagementService;

    public AuthenticationService(AccountRepo accountRepo, PasswordEncoder passwordEncoder, TokenManagementService tokenManagementService) {
        this.accountRepo = accountRepo;
        this.passwordEncoder = passwordEncoder;
        this.tokenManagementService = tokenManagementService;
    }

    public String authenticate(String username, String password) {
        Optional<Account> account = accountRepo.findByUsername(username);

        if (!account.isPresent()) {
            //Non-existing user case handling
            throw new UserNotFoundException(username);
        }
        else{
            String storedPassword = account.get().getPassword();
            if (!passwordEncoder.matches(password, storedPassword)) {
                //Handle invalid password case
                throw new IncorrectPasswordException();
            }
            return tokenManagementService.generateJWT(username);
        }
    }
}

