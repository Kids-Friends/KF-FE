package com.kidsFriend.data.model;

public class PhotoRequest {
    public String clientId;
    public String photoUrl;
    public String photoName;

    public PhotoRequest(String clientId, String photoUrl, String photoName) {
        this.clientId = clientId;
        this.photoUrl = photoUrl;
        this.photoName = photoName;
    }
}
