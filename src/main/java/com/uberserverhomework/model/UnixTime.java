package com.uberserverhomework.model;

import java.time.Instant;

public class UnixTime implements Time{
    @Override
    public String getNow() {
        return Instant.now().toString();
    }
}
