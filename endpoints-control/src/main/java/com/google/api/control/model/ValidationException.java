/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.control.model;

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.Stack;

/**
 * Exception thrown if there is a validation problem with a path template, http config, or related
 * framework methods. Comes as an illegal argument exception subclass. Allows to globally
 * set a thread-local validation context description which each exception inherits.
 */
@Beta
public class ValidationException extends IllegalArgumentException {

  private static ThreadLocal<Stack<Supplier<String>>> contextLocal =
      new ThreadLocal<Stack<Supplier<String>>>();

  /**
   * Sets the validation context description. Each thread has its own description, so
   * this is thread safe.
   */
  public static void pushCurrentThreadValidationContext(Supplier<String> supplier) {
    Stack<Supplier<String>> stack = contextLocal.get();
    if (stack == null) {
      stack = new Stack<>();
      contextLocal.set(stack);
    }
    stack.push(supplier);
  }

  public static void pushCurrentThreadValidationContext(String context) {
    pushCurrentThreadValidationContext(Suppliers.ofInstance(context));
  }
  /**
   * Clears the validation context.
   */
  public static void popCurrentThreadValidationContext() {
    Stack<?> stack = contextLocal.get();
    if (stack != null) {
      stack.pop();
    }
  }

  /**
   * Construct validation exception with implicit context.
   */
  public ValidationException(String format, Object... args) {
    super(message(contextLocal.get(), format, args));
  }

  private static String message(Stack<Supplier<String>> context, String format, Object... args) {
    if (context == null || context.isEmpty()) {
      return String.format(format, args);
    }
    StringBuilder result = new StringBuilder();
    for (Supplier<String> supplier : context) {
      result.append(supplier.get() + ": ");
    }
    return result.toString() + String.format(format, args);
  }
}
