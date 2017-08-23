package com.google.api.control.util;

import com.google.common.base.CharMatcher;

/**
 * String utilities.
 */
public final class StringUtils {
  /**
   * Strips the trailing slash from a string if it has a trailing slash; otherwise return the
   * string unchanged.
   */
  public static String stripTrailingSlash(String s) {
    return s == null ? null : CharMatcher.is('/').trimTrailingFrom(s);
  }

  private StringUtils() { }
}
