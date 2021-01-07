/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classloader;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;

public class BackwardCompatibilityClassTransformer {

    public static byte[] transform(String className, byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new TransformingAdapter(classWriter), 0);
        bytes = classWriter.toByteArray();
        return bytes;
    }

    private static class TransformingAdapter extends ClassVisitor {

        private List<RemovedMethodInfo> removedMethods = new LinkedList<>();
        private String className;

        public TransformingAdapter(ClassVisitor cv) {
            super(ASM7, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MyMethodVisitor(this, name, descriptor, super.visitMethod(access, name, descriptor, signature, exceptions));
        }

        @Override
        public void visitEnd() {
            addBridgeMethods();
            super.visitEnd();
        }

        private void addBridgeMethods() {
            for (RemovedMethodInfo removedMethod : Collections.unmodifiableList(removedMethods)) {
                Type[] argumentTypes = removedMethod.getMethodType().getArgumentTypes();
                Type returnType = removedMethod.getMethodType().getReturnType();
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PUBLIC, removedMethod.getOriginalName(), removedMethod.getRemovedMethodDescriptor(), null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitVarInsn(ALOAD, 0);
                for (int i = 0; i < argumentTypes.length; i++) {
                    methodVisitor.visitVarInsn(ALOAD, i + 1);
                }
                methodVisitor.visitMethodInsn(INVOKESPECIAL, className, removedMethod.getRemovedMethodName(), removedMethod.getRemovedMethodDescriptor(), false);
                if (!returnType.equals(Type.VOID_TYPE)) {
                    methodVisitor.visitInsn(ARETURN);
                } else {
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitInsn(RETURN);
                }
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLocalVariable("this", "L" + className + ";", null, label0, label2, 0);
                for (int i = 0; i < removedMethod.getMethodType().getArgumentTypes().length; i++) {//
                    methodVisitor.visitLocalVariable("arg" + i, argumentTypes[i].getDescriptor(), null, label0, label2, i + 1);
                }
                methodVisitor.visitMaxs(argumentTypes.length + 1, argumentTypes.length + 1);
                methodVisitor.visitEnd();
            }
        }

        public void addRemovedMethod(RemovedMethodInfo removedMethodInfo) {
            removedMethods.add(removedMethodInfo);
        }
    }

    private static class MyMethodVisitor extends MethodVisitor {

        private static final String REMOVED_ANNOTATION_DESCRIPTOR = "Lorg/gradle/api/model/Removed;";
        private final TransformingAdapter transformingAdapter;
        private final String methodName;
        private final String methodDescriptor;

        public MyMethodVisitor(TransformingAdapter transformingAdapter, String methodName, String methodDescriptor, MethodVisitor methodVisitor) {
            super(ASM7, methodVisitor);
            this.transformingAdapter = transformingAdapter;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(REMOVED_ANNOTATION_DESCRIPTOR)) {
                return new MyAnnotationVisitor(transformingAdapter, methodName, methodDescriptor, super.visitAnnotation(descriptor, visible));
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

    private static class MyAnnotationVisitor extends AnnotationVisitor {

        private static final String REMOVED_ANNOTATION_ORIGINAL = "original";
        private final String methodName;
        private final String methodDescriptor;
        private final TransformingAdapter transformingAdapter;

        public MyAnnotationVisitor(TransformingAdapter transformingAdapter, String methodName, String methodDescriptor, AnnotationVisitor annotationVisitor) {
            super(ASM7, annotationVisitor);
            this.transformingAdapter = transformingAdapter;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public void visit(String name, Object value) {
            if (REMOVED_ANNOTATION_ORIGINAL.equals(name)) {
                transformingAdapter.addRemovedMethod(new RemovedMethodInfo(methodName, methodDescriptor, (String) value));
            }
            super.visit(name, value);
        }
    }

    private static class RemovedMethodInfo {
        private final String removedMethodName;
        private final String removedMethodDescriptor;
        private final String originalName;
        private final Type methodType;

        public RemovedMethodInfo(String removedMethodName, String removedMethodDescriptor, String originalName) {
            this.removedMethodName = removedMethodName;
            this.removedMethodDescriptor = removedMethodDescriptor;
            this.originalName = originalName;
            this.methodType = Type.getMethodType(removedMethodDescriptor);
        }

        public String getRemovedMethodName() {
            return removedMethodName;
        }

        public String getRemovedMethodDescriptor() {
            return removedMethodDescriptor;
        }

        public String getOriginalName() {
            return originalName;
        }

        public Type getMethodType() {
            return methodType;
        }
    }
}
