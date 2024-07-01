package me.youngjibbit.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE_USE)
@Retention(value = RetentionPolicy.RUNTIME)

public @interface CommandInfo
{
    String name();
    String description();
    String usage() default "";
    String[] aliases() default "";
}
