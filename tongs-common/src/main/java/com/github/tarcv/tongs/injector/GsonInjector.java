/*
 * Copyright 2020 TarCV
 * Copyright 2014 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector;

import static com.github.tarcv.tongs.ComputedPooling.Characteristic.valueOf;

import com.github.tarcv.tongs.ComputedPooling;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.io.File;
import java.time.Instant;

public class GsonInjector {
    private static final Gson GSON;
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();

    static {
        GSON_BUILDER.registerTypeAdapter(ComputedPooling.Characteristic.class, characteristicDeserializer());
        GSON_BUILDER.registerTypeAdapter(ComputedPooling.Characteristic.class, characteristicSerializer());
        GSON_BUILDER.registerTypeAdapter(Class.class, classSerializer());
        GSON_BUILDER.registerTypeAdapter(Class.class, classDeserializer());
        GSON_BUILDER.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> Instant.ofEpochMilli(json.getAsLong()));
        GSON_BUILDER.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toEpochMilli()));
        GSON_BUILDER.registerTypeAdapter(File.class, (JsonDeserializer<File>) (json, typeOfT, context) -> new File(json.getAsString()));
        GSON_BUILDER.registerTypeAdapter(File.class, (JsonSerializer<File>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()));
        GSON = GSON_BUILDER.create();
    }

    private GsonInjector() {}

    private static JsonSerializer<ComputedPooling.Characteristic> characteristicSerializer() {
        return (src, typeOfSrc, context) -> new JsonPrimitive(src.name());
    }

    private static JsonDeserializer<ComputedPooling.Characteristic> characteristicDeserializer() {
        return (json, typeOfT, context) -> valueOf(json.getAsJsonPrimitive().getAsString());
    }

    public static JsonSerializer<Class<?>> classSerializer() {
        return (src, typeOfSrc, context) -> new JsonPrimitive(src.getName());
    }

    private static JsonDeserializer<Class<?>> classDeserializer() {
        return (json, typeOfT, context) -> {
            try {
                return Class.forName(json.getAsJsonPrimitive().getAsString());
            } catch (ClassNotFoundException e) {
                throw new JsonParseException("Couldn't deserialize a class", e);
            }
        };
    }

    public static Gson gson() {
        return GSON;
    }
}
