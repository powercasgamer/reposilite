/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.configuration.shared

import com.fasterxml.classmate.ResolvedType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.victools.jsonschema.generator.CustomDefinition
import com.github.victools.jsonschema.generator.FieldScope
import com.github.victools.jsonschema.generator.MemberScope
import com.github.victools.jsonschema.generator.MethodScope
import com.github.victools.jsonschema.generator.Module
import com.github.victools.jsonschema.generator.Option.ADDITIONAL_FIXED_TYPES
import com.github.victools.jsonschema.generator.Option.ALLOF_CLEANUP_AT_THE_END
import com.github.victools.jsonschema.generator.Option.EXTRA_OPEN_API_FORMAT_VALUES
import com.github.victools.jsonschema.generator.Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS
import com.github.victools.jsonschema.generator.Option.FLATTENED_ENUMS
import com.github.victools.jsonschema.generator.Option.FLATTENED_OPTIONALS
import com.github.victools.jsonschema.generator.Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT
import com.github.victools.jsonschema.generator.Option.GETTER_METHODS
import com.github.victools.jsonschema.generator.Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES
import com.github.victools.jsonschema.generator.Option.NONPUBLIC_NONSTATIC_FIELDS_WITHOUT_GETTERS
import com.github.victools.jsonschema.generator.Option.NONPUBLIC_NONSTATIC_FIELDS_WITH_GETTERS
import com.github.victools.jsonschema.generator.Option.PLAIN_DEFINITION_KEYS
import com.github.victools.jsonschema.generator.Option.PUBLIC_NONSTATIC_FIELDS
import com.github.victools.jsonschema.generator.Option.SCHEMA_VERSION_INDICATOR
import com.github.victools.jsonschema.generator.Option.VALUES_FROM_CONSTANT_FIELDS
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerationContext
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaKeyword
import com.github.victools.jsonschema.generator.TypeScope
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Max
import com.reposilite.configuration.shared.api.Min
import com.reposilite.configuration.shared.api.Range
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

val SCHEMA_OPTION_PRESET = OptionPreset(
    SCHEMA_VERSION_INDICATOR,
    ADDITIONAL_FIXED_TYPES,
    FLATTENED_ENUMS,
    FLATTENED_OPTIONALS,
    VALUES_FROM_CONSTANT_FIELDS,
    PUBLIC_NONSTATIC_FIELDS,
    NONPUBLIC_NONSTATIC_FIELDS_WITH_GETTERS,
    NONPUBLIC_NONSTATIC_FIELDS_WITHOUT_GETTERS,
    ALLOF_CLEANUP_AT_THE_END,
    MAP_VALUES_AS_ADDITIONAL_PROPERTIES,
    PLAIN_DEFINITION_KEYS,
    EXTRA_OPEN_API_FORMAT_VALUES,
    FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS,
    GETTER_METHODS,
    FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT
)

fun interface SubtypeResolver {
    fun resolve(declaredType: ResolvedType, context: SchemaGenerationContext): List<ResolvedType>?
}

fun interface EnumResolver {
    fun resolve(scope: FieldScope): Collection<*>?
}

class SettingsModule(
    private val subtypeResolvers: Collection<SubtypeResolver> = listOf(),
    private val enumResolvers: Collection<EnumResolver> = listOf()
) : Module {

    override fun applyToConfigBuilder(builder: SchemaGeneratorConfigBuilder) {
        builder.forTypesInGeneral().withCustomDefinitionProvider { declaredType, context ->
            val subtypes = subtypeResolvers.firstNotNullOfOrNull { it.resolve(declaredType, context) } ?: return@withCustomDefinitionProvider null
            val definition: ObjectNode = context.generatorConfig.createObjectNode()
            val subtypeArray: ArrayNode = definition.withArray(context.getKeyword(SchemaKeyword.TAG_ONEOF))
            subtypes.forEach { subtype ->
                subtypeArray.add(context.createDefinition(subtype))
            }
            return@withCustomDefinitionProvider CustomDefinition(
                definition,
                CustomDefinition.DefinitionType.STANDARD,
                CustomDefinition.AttributeInclusion.NO
            )
        }

        builder.forFields().withEnumResolver {
            enumResolvers.firstNotNullOfOrNull { resolver -> resolver.resolve(it) }
        }

        builder.forFields().withNumberInclusiveMinimumResolver { it.getAnnotationConsideringFieldAndGetterIfSupported(
            Min::class.java
        )?.min?.toBigDecimal() }
        builder.forFields().withNumberInclusiveMaximumResolver { it.getAnnotationConsideringFieldAndGetterIfSupported(
            Max::class.java
        )?.max?.toBigDecimal() }
        builder.forFields().withNumberInclusiveMinimumResolver { it.getAnnotationConsideringFieldAndGetterIfSupported(
            Range::class.java
        )?.min?.toBigDecimal() }
        builder.forFields().withNumberInclusiveMaximumResolver { it.getAnnotationConsideringFieldAndGetterIfSupported(
            Range::class.java
        )?.max?.toBigDecimal() }

        builder.forFields().withTitleResolver { it.doc?.title }
        builder.forMethods().withTitleResolver { it.doc?.title }
        builder.forTypesInGeneral().withTitleResolver { it.doc?.title }

        builder.forFields().withDescriptionResolver { it.doc?.description?.trimIndent() }
        builder.forMethods().withDescriptionResolver { it.doc?.description?.trimIndent() }
        builder.forTypesInGeneral().withDescriptionResolver { it.doc?.description?.trimIndent() }

        builder.forFields().withNullableCheck { it.kProperty?.returnType?.isMarkedNullable }

        // Obviously, jsonforms is going crazy with default values and is not able to match proper scheme in 'oneOf' directive
        // builder.forFields().withDefaultResolver(defaultValueResolve())

        builder.forTypesInGeneral().withPropertySorter { _, _ -> 0 }
    }

    /**
     * Based on https://victools.github.io/jsonschema-generator/#how-to-populate-default-values
     */
//    private fun defaultValueResolve(): ConfigFunction<FieldScope, Any?> {
//        val instanceCache = ConcurrentHashMap<Class<*>, Any>()
//
//        return ConfigFunction { field ->
//            runCatching {
//                val declaringClass = field.declaringType.erasedType
//
//                if (!field.isFakeContainerItemScope && declaringClass.name.startsWith("com.reposilite")) {
//                    val instance = instanceCache.computeIfAbsent(declaringClass) { declaringClass.getConstructor().newInstance() }
//                    field.findGetter().rawMember.invoke(instance)
//                } else null
//            }.fold(
//                onSuccess = { it },
//                onFailure = {
//                    it.printStackTrace() // most likely missing a no-args constructor
//                    null
//                }
//            )
//        }
//    }

    private val MemberScope<*, *>.kProperty: KProperty1<*, *>?
        get() = this.declaringType.erasedType.kotlin.memberProperties.find { it.name == this.name }

    private val MethodScope.kProperty: KProperty1<*, *>?
        get() = this.declaringType.erasedType.kotlin.memberProperties.find { it.getter.name == this.name }

    private val FieldScope.doc: Doc?
        get() = this.getAnnotationConsideringFieldAndGetterIfSupported(Doc::class.java) ?: this.kProperty?.findAnnotation()

    private val MethodScope.doc: Doc?
        get() = this.getAnnotationConsideringFieldAndGetterIfSupported(Doc::class.java) ?: this.kProperty?.findAnnotation()

    private val TypeScope.doc: Doc?
        get() = this.type.erasedType.getAnnotation(Doc::class.java)

}

internal fun cleanupScheme(node: JsonNode) {
    when (node) {
        is ObjectNode -> {
            if (node.has("items") && node.get("items").isObject && node.get("items").has("allOf") && node.get("items").get(
                    "allOf"
                ).isArray && node.get("items").get("allOf").size() == 2) {
                when {
                    !node.get("items").get("allOf").get(1).has("type") -> node.set<JsonNode>(
                        "items",
                        node.get("items").get("allOf").get(0)
                    )
                    !node.get("items").get("allOf").get(0).has("type") -> node.set<JsonNode>(
                        "items",
                        node.get("items").get("allOf").get(1)
                    )
                }
            }
            node.fields().forEach {
                cleanupScheme(it.value)
            }
        }
        is ArrayNode -> node.forEach {
            cleanupScheme(it)
        }
    }
}
