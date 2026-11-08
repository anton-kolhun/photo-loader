package com.photoloader.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Put on every bean of the search feature: it only exists when clip.search.enabled=true. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(name = "clip.search.enabled", havingValue = "true")
public @interface ConditionalOnClipSearch {
}
