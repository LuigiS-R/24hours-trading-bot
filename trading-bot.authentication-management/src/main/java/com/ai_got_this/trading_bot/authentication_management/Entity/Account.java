package com.ai_got_this.trading_bot.authentication_management.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Account {
    //Account entity class representing the user account in the database. It's primary key is autoincremented by the DB Engine, it has a username, email_address and password. The username and email_address are unique and cannot be null. The password is hashed using bcrypt before being stored in the database.
    @Id
    private Integer id;
    private String username;
    private String email_address;
    private String password;

    public Account() {
    }

    public Account(String username, String email_address, String password) {
        this.username = username;
        this.email_address = email_address;
        this.password = password;
    }
}
