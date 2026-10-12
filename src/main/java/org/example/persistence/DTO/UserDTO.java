package org.example.persistence.DTO;

public class UserDTO {
    private long id;
    private String loginId;
    private String password;
    private String name;
    private String phoneNumber;
    private String role;
    private String status;

    public UserDTO(long id, String loginId, String password,
                   String name, String phoneNumber,
                   String role, String status) {
        this.id = id;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}