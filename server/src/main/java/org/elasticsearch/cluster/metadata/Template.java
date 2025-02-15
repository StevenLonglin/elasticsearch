/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.cluster.AbstractDiffable;
import org.elasticsearch.common.util.Maps;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.mapper.MapperService;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A template consists of optional settings, mappings, or alias configuration for an index, however,
 * it is entirely independent from an index. It's a building block forming part of a regular index
 * template and a {@link ComponentTemplate}.
 */
public class Template extends AbstractDiffable<Template> implements ToXContentObject {
    private static final ParseField SETTINGS = new ParseField("settings");
    private static final ParseField MAPPINGS = new ParseField("mappings");
    private static final ParseField ALIASES = new ParseField("aliases");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<Template, Void> PARSER = new ConstructingObjectParser<>("template", false,
        a -> new Template((Settings) a[0], (CompressedXContent) a[1], (Map<String, AliasMetadata>) a[2]));

    static {
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), (p, c) -> Settings.fromXContent(p), SETTINGS);
        PARSER.declareField(ConstructingObjectParser.optionalConstructorArg(), (p, c) -> {
            XContentParser.Token token = p.currentToken();
            if (token == XContentParser.Token.VALUE_STRING) {
                return new CompressedXContent(Base64.getDecoder().decode(p.text()));
            } else if(token == XContentParser.Token.VALUE_EMBEDDED_OBJECT){
                return new CompressedXContent(p.binaryValue());
            } else if (token == XContentParser.Token.START_OBJECT){
                return new CompressedXContent(Strings.toString(XContentFactory.jsonBuilder().map(p.mapOrdered())));
            } else {
                throw new IllegalArgumentException("Unexpected token: " + token);
            }
        }, MAPPINGS, ObjectParser.ValueType.VALUE_OBJECT_ARRAY);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), (p, c) -> {
            Map<String, AliasMetadata> aliasMap = new HashMap<>();
            while ((p.nextToken()) != XContentParser.Token.END_OBJECT) {
                AliasMetadata alias = AliasMetadata.Builder.fromXContent(p);
                aliasMap.put(alias.alias(), alias);
            }
            return aliasMap;
        }, ALIASES);
    }

    @Nullable
    private final Settings settings;
    @Nullable
    private final CompressedXContent mappings;
    @Nullable
    private final Map<String, AliasMetadata> aliases;

    public Template(@Nullable Settings settings, @Nullable CompressedXContent mappings, @Nullable Map<String, AliasMetadata> aliases) {
        this.settings = settings;
        this.mappings = mappings;
        this.aliases = aliases;
    }

    public Template(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.settings = Settings.readSettingsFromStream(in);
        } else {
            this.settings = null;
        }
        if (in.readBoolean()) {
            this.mappings = CompressedXContent.readCompressedString(in);
        } else {
            this.mappings = null;
        }
        if (in.readBoolean()) {
            this.aliases = in.readMap(StreamInput::readString, AliasMetadata::new);
        } else {
            this.aliases = null;
        }
    }

    @Nullable
    public Settings settings() {
        return settings;
    }

    @Nullable
    public CompressedXContent mappings() {
        return mappings;
    }

    @Nullable
    public Map<String, AliasMetadata> aliases() {
        return aliases;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (this.settings == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            Settings.writeSettingsToStream(this.settings, out);
        }
        if (this.mappings == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            this.mappings.writeTo(out);
        }
        if (this.aliases == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeMap(this.aliases, StreamOutput::writeString, (stream, aliasMetadata) -> aliasMetadata.writeTo(stream));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(settings, mappings, aliases);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        Template other = (Template) obj;
        return Objects.equals(settings, other.settings) &&
            mappingsEquals(this.mappings, other.mappings) &&
            Objects.equals(aliases, other.aliases);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (this.settings != null) {
            builder.startObject(SETTINGS.getPreferredName());
            this.settings.toXContent(builder, params);
            builder.endObject();
        }
        if (this.mappings != null) {
            String context = params.param(Metadata.CONTEXT_MODE_PARAM, Metadata.CONTEXT_MODE_API);
            boolean binary = params.paramAsBoolean("binary", false);
            if (Metadata.CONTEXT_MODE_API.equals(context) || binary == false) {
                Map<String, Object> uncompressedMapping =
                    XContentHelper.convertToMap(this.mappings.uncompressed(), true, XContentType.JSON).v2();
                if (uncompressedMapping.size() > 0) {
                    builder.field(MAPPINGS.getPreferredName());
                    builder.map(reduceMapping(uncompressedMapping));
                }
            } else {
                builder.field(MAPPINGS.getPreferredName(), mappings.compressed());
            }
        }
        if (this.aliases != null) {
            builder.startObject(ALIASES.getPreferredName());
            for (AliasMetadata alias : this.aliases.values()) {
                AliasMetadata.Builder.toXContent(alias, builder, params);
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> reduceMapping(Map<String, Object> mapping) {
        if (mapping.size() == 1 && MapperService.SINGLE_MAPPING_NAME.equals(mapping.keySet().iterator().next())) {
            return (Map<String, Object>) mapping.values().iterator().next();
        } else {
            return mapping;
        }
    }

    static boolean mappingsEquals(CompressedXContent m1, CompressedXContent m2) {
        if (m1 == m2) {
            return true;
        }

        if (m1 == null || m2 == null) {
            return false;
        }

        if (m1.equals(m2)) {
            return true;
        }

        Map<String, Object> thisUncompressedMapping = reduceMapping(
            XContentHelper.convertToMap(m1.uncompressed(), true, XContentType.JSON).v2()
        );
        Map<String, Object> otherUncompressedMapping = reduceMapping(
            XContentHelper.convertToMap(m2.uncompressed(), true, XContentType.JSON).v2()
        );
        return Maps.deepEquals(thisUncompressedMapping, otherUncompressedMapping);
    }
}
