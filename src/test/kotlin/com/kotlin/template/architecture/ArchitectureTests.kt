package com.kotlin.template.architecture

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.asm.ClassReader
import org.springframework.asm.ClassVisitor
import org.springframework.asm.FieldVisitor
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Opcodes
import org.springframework.asm.Type
import com.kotlin.template.TemplateApplication
import java.io.File

/** Checks compiled dependencies, including calls inside methods, without starting Spring or Docker. */
class ArchitectureTests {
    private val prefix = "com.kotlin.template."
    private val contexts = setOf("customer", "identity", "audit", "notification")
    private val dependencies = File(TemplateApplication::class.java.protectionDomain.codeSource.location.toURI())
        .resolve("com/kotlin/template").walkTopDown().filter { it.extension == "class" }
        .associate { resource ->
            val references = mutableSetOf<String>()
            fun reference(name: String?) {
                if (name != null) {
                    val normalized = if (name.startsWith("[")) name.trimStart('[').removePrefix("L").removeSuffix(";") else name
                    if (normalized.length > 1) references.add(normalized.replace('/', '.'))
                }
            }
            fun type(type: Type) {
                when (type.sort) {
                    Type.OBJECT -> reference(type.internalName)
                    Type.ARRAY -> type(type.elementType)
                    Type.METHOD -> {
                        type.argumentTypes.forEach { type(it) }
                        type(type.returnType)
                    }
                }
            }
            fun descriptor(value: String) = type(Type.getType(value))
            fun signature(value: String?) {
                // JVM generic signatures encode class references as Lpackage/Type; or Lpackage/Type<...>.
                if (value != null) Regex("L([A-Za-z0-9_/$]+)").findAll(value)
                    .map { it.groupValues[1] }.filter { '/' in it }.forEach { reference(it) }
            }
            val reader = resource.inputStream().use { ClassReader(it) }
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                    signature(signature)
                    reference(superName)
                    interfaces?.forEach { reference(it) }
                }

                override fun visitAnnotation(descriptor: String, visible: Boolean): org.springframework.asm.AnnotationVisitor? {
                    descriptor(descriptor)
                    return null
                }

                override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    descriptor(descriptor)
                    signature(signature)
                    return null
                }

                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    descriptor(descriptor)
                    signature(signature)
                    exceptions?.forEach { reference(it) }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String, visible: Boolean): org.springframework.asm.AnnotationVisitor? {
                            descriptor(descriptor)
                            return null
                        }

                        override fun visitTypeInsn(opcode: Int, type: String) = reference(type)
                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                            reference(owner)
                            descriptor(descriptor)
                        }

                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            reference(owner)
                            descriptor(descriptor)
                        }

                        override fun visitLdcInsn(value: Any?) {
                            if (value is Type) type(value)
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            reader.className.replace('/', '.') to references
        }

    init {
        check(dependencies.keys.any { ".domain." in it }) { "Production classes were not found" }
    }

    @Test
    fun `domain remains independent of frameworks and other layers`() {
        val violations = dependencies.filterKeys { ".domain." in it }.flatMap { (owner, references) ->
            val domain = owner.substringBefore(".domain.") + ".domain."
            references.filterNot {
                it.startsWith(domain) || it.startsWith("java.") || it.startsWith("kotlin.") ||
                    it.startsWith("org.jetbrains.annotations.")
            }.map { "$owner -> $it" }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `application and HTTP adapters respect layer boundaries`() {
        val violations = dependencies.flatMap { (owner, references) ->
            references.filter { target ->
                target.startsWith(prefix) && (
                    (".application." in owner && (".infrastructure." in target || ".interfaces." in target)) ||
                        (".interfaces.rest." in owner && (
                            ".infrastructure." in target || ".domain.repository." in target || ".domain.model." in target
                        ))
                    )
            }.map { "$owner -> $it" }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `contexts only access another context through its application API`() {
        val violations = dependencies.flatMap { (owner, references) ->
            val context = owner.removePrefix(prefix).substringBefore('.')
            if (context !in contexts) emptyList() else references.filter { target ->
                val other = target.removePrefix(prefix).substringBefore('.')
                target.startsWith(prefix) && other in contexts && other != context &&
                    !listOf("contract", "usecase", "result").any { target.startsWith("$prefix$other.application.$it.") }
            }.map { "$owner -> $it" }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `context dependencies contain no cycles`() {
        val graph = contexts.associateWith { context ->
            dependencies.filterKeys { it.startsWith("$prefix$context.") }.values.flatten()
                .filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('.') }
                .filter { it in contexts && it != context }.toSet()
        }
        fun visit(context: String, path: List<String>) {
            assertTrue(context !in path, "Context cycle: ${(path + context).joinToString(" -> ")}")
            graph.getValue(context).forEach { visit(it, path + context) }
        }
        contexts.forEach { visit(it, emptyList()) }
    }
}
