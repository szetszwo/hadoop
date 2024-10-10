/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.security;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** For handling customized {@link Callback}. */
public interface CustomizedCallbackHandler {
  class DefaultHandler implements CustomizedCallbackHandler{
    @Override
    public void handleCallbacks(List<Callback> callbacks, String username, char[] password)
        throws UnsupportedCallbackException {
      if (!callbacks.isEmpty()) {
        final Callback cb = callbacks.get(0);
        throw new UnsupportedCallbackException(callbacks.get(0),
            "Unsupported callback: " + (cb == null ? null : cb.getClass()));
      }
    }
  }

  static CustomizedCallbackHandler delegate(Object delegated) {
    final String methodName = "handleCallbacks";
    final Class<?> clazz = delegated.getClass();
    final Method method;
    try {
      method = clazz.getMethod(methodName, List.class, String.class, char[].class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Failed to get method " + methodName + " from " + clazz, e);
    }

    return (callbacks, name, password) -> {
      try {
        method.invoke(delegated, callbacks, name, password);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IOException("Failed to invoke " + method, e);
      }
    };
  }

  static CustomizedCallbackHandler get(Configuration conf) {
    final Class<?> clazz = conf.getClass(
        CommonConfigurationKeysPublic.HADOOP_SECURITY_SASL_CUSTOMIZEDCALLBACKHANDLER_CLASS_KEY,
        DefaultHandler.class);
    final Object handler;
    try {
      handler = clazz.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create a new instance of " + clazz, e);
    }
    return handler instanceof CustomizedCallbackHandler ? (CustomizedCallbackHandler) handler
        : CustomizedCallbackHandler.delegate(handler);
  }

  void handleCallbacks(List<Callback> callbacks, String name, char[] password)
      throws UnsupportedCallbackException, IOException;
}
