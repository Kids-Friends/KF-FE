package com.kidsFriend.global.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T data);

    void onError(String message);
}
