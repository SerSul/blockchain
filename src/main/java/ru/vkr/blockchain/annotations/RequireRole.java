package ru.vkr.blockchain.annotations;

import ru.vkr.blockchain.domain.model.enums.AccountRole;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    AccountRole[] value();

    boolean requireAll() default false;
}
