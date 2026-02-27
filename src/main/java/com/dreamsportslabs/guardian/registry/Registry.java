package com.dreamsportslabs.guardian.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.keyvalue.MultiKey;

@Slf4j
public class Registry {
  private final Map<MultiKey, Object> classMap;

  private static final String DEFAULT_NAME = "default";
  private static final String GLOBAL_SCOPE = "__global__";

  public Registry() {
    this.classMap = new HashMap<>();
  }

  public <T> T get(String tenant, Class<T> clazz) {
    return this.get(tenant, clazz, DEFAULT_NAME);
  }

  public <T> T get(String tenant, Class<T> clazz, String name) {
    return (T) classMap.get(getKey(clazz, tenant, name));
  }

  public <T> void put(String tenant, T object) {
    this.put(tenant, object, DEFAULT_NAME);
  }

  public <T> void put(String tenant, T object, String name) {
    Objects.requireNonNull(object);
    classMap.put(getKey(object.getClass(), tenant, name), object);
  }

  public <T> T getGlobal(Class<T> clazz, String name) {
    return this.get(GLOBAL_SCOPE, clazz, name);
  }

  public <T> void putGlobal(T object, String name) {
    this.put(GLOBAL_SCOPE, object, name);
  }

  private MultiKey getKey(Class<?> clazz, String tenant, String name) {
    return new MultiKey(tenant, clazz.getName(), name);
  }
}
