package org.example.dto;

public class LoginUser {
    private final long id;
    private final String loginId;
    private final String name;
    private final String role;

    public LoginUser(long id, String loginId, String name, String role) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.role = role;
    }
    public long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public String getRole() { return role; }
}
