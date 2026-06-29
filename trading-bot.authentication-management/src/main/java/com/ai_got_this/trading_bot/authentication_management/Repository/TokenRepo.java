package com.ai_got_this.trading_bot.authentication_management.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ai_got_this.trading_bot.authentication_management.Entity.Token;

public interface TokenRepo extends JpaRepository<Token, Long>{
}
