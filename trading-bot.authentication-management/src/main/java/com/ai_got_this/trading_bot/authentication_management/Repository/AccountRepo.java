package com.ai_got_this.trading_bot.authentication_management.Repository;

import org.springframework.stereotype.Repository;
import com.ai_got_this.trading_bot.authentication_management.Entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

@Repository
public interface AccountRepo extends JpaRepository<Account, Integer> {
    //This interface will be used to interact with the database and perform CRUD operations on the Account entity

    //Method to find an account by username
    Optional<Account> findByUsername(String username);
}
