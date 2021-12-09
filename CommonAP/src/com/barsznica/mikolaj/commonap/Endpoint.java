package com.barsznica.mikolaj.commonap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Endpoint {
    String path();
    HttpMethod method() default HttpMethod.Get;
}
