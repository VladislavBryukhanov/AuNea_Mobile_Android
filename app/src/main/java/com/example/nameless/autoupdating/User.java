package com.example.nameless.autoupdating;

import java.io.Serializable;

/**
 * Created by nameless on 07.04.18.
 */

public class User implements Serializable{

    private String login;
    private String uid;
    private String email;
    private String nickname;
    private String bio;
    //avatar, bio ...

    public User(){}

    public User(String uid, String login) {
        this.uid = uid;
        this.login = login;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
