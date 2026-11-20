package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'ROLE_USER'")
    private String role = "ROLE_USER";

    protected User() {}

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = "ROLE_USER";
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
