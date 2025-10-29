/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson;

import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

class BuilderHelper {
  private BuilderHelper() {}

  @SuppressWarnings("unchecked")
  static <E> List<E> unmodifiableList(Collection<E> collection) {
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }
    if (collection.size() == 1) {
      return Collections.singletonList(
          collection instanceof List
              ? ((List<E>) collection).get(0)
              : collection.iterator().next());
    }
    return (List<E>)
        Collections.unmodifiableList(
            collection instanceof List
                ? (List<E>) collection
                : Arrays.asList(collection.toArray()));
  }

  private static final TypeAdapter<Number> DOUBLE_WITH_CHECK =
      new TypeAdapter<Number>() {
        @Override
        public Double read(JsonReader in) throws IOException {
          if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
          }
          return in.nextDouble();
        }

        @Override
        public void write(JsonWriter out, Number value) throws IOException {
          if (value == null) {
            out.nullValue();
            return;
          }
          double doubleValue = value.doubleValue();
          checkValidFloatingPoint(doubleValue);
          out.value(doubleValue);
        }
      };

  private static final TypeAdapter<Number> FLOAT_WITH_CHECK =
      new TypeAdapter<Number>() {
        @Override
        public Float read(JsonReader in) throws IOException {
          if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
          }
          return (float) in.nextDouble();
        }

        @Override
        public void write(JsonWriter out, Number value) throws IOException {
          if (value == null) {
            out.nullValue();
            return;
          }
          float floatValue = value.floatValue();
          checkValidFloatingPoint(floatValue);
          // For backward compatibility don't call `JsonWriter.value(float)` because that method has
          // been newly added and not all custom JsonWriter implementations might override it yet
          Number floatNumber = value instanceof Float ? value : floatValue;
          out.value(floatNumber);
        }
      };

  private static final TypeAdapter<Number> LONG_AS_STRING =
      new TypeAdapter<Number>() {
        @Override
        public Number read(JsonReader in) throws IOException {
          if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
          }
          return in.nextLong();
        }

        @Override
        public void write(JsonWriter out, Number value) throws IOException {
          if (value == null) {
            out.nullValue();
            return;
          }
          out.value(value.toString());
        }
      };

  static TypeAdapter<Number> doubleAdapter(boolean serializeSpecialFloatingPointValues) {
    return serializeSpecialFloatingPointValues ? TypeAdapters.DOUBLE : DOUBLE_WITH_CHECK;
  }

  static TypeAdapter<Number> floatAdapter(boolean serializeSpecialFloatingPointValues) {
    return serializeSpecialFloatingPointValues ? TypeAdapters.FLOAT : FLOAT_WITH_CHECK;
  }

  private static void checkValidFloatingPoint(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(
          value
              + " is not a valid double value as per JSON specification. To override this"
              + " behavior, use GsonBuilder.serializeSpecialFloatingPointValues() method.");
    }
  }

  static TypeAdapter<Number> longAdapter(LongSerializationPolicy longSerializationPolicy) {
    return longSerializationPolicy == LongSerializationPolicy.DEFAULT
        ? TypeAdapters.LONG
        : LONG_AS_STRING;
  }

  static TypeAdapter<AtomicLong> atomicLongAdapter(TypeAdapter<Number> longAdapter) {
    return new TypeAdapter<AtomicLong>() {
      @Override
      public void write(JsonWriter out, AtomicLong value) throws IOException {
        longAdapter.write(out, value.get());
      }

      @Override
      public AtomicLong read(JsonReader in) throws IOException {
        Number value = longAdapter.read(in);
        return new AtomicLong(value.longValue());
      }
    }.nullSafe();
  }

  static TypeAdapter<AtomicLongArray> atomicLongArrayAdapter(TypeAdapter<Number> longAdapter) {
    return new TypeAdapter<AtomicLongArray>() {
      @Override
      public void write(JsonWriter out, AtomicLongArray value) throws IOException {
        out.beginArray();
        for (int i = 0, length = value.length(); i < length; i++) {
          longAdapter.write(out, value.get(i));
        }
        out.endArray();
      }

      @Override
      public AtomicLongArray read(JsonReader in) throws IOException {
        // Simulates ArrayList growth behavior
        long[] array = new long[10];
        int count = 0;
        in.beginArray();
        while (in.hasNext()) {
          long value = longAdapter.read(in).longValue();
          if (count >= array.length) {
            array = Arrays.copyOf(array, count + (count >> 1));
          }
          array[count++] = value;
        }
        in.endArray();
        if (count != array.length) {
          array = Arrays.copyOf(array, count);
        }
        return new AtomicLongArray(array);
      }
    }.nullSafe();
  }
}
