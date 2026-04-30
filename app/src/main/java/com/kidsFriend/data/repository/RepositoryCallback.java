package com.kidsFriend.data.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T data);

    void onError(String message);
}
