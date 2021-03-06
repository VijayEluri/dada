/*
 * Copyright (c) 2009, Julian Gosnell
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.dada.asm;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ClassFactory implements Opcodes {

	
	private final Map<String, String> typeToCode = new HashMap<String, String>();
	
	{
		typeToCode.put(byte.class.getCanonicalName(),      "B");
		typeToCode.put(short.class.getCanonicalName(),     "S");
		typeToCode.put(int.class.getCanonicalName(),       "I");
		typeToCode.put(long.class.getCanonicalName(),      "J");
		typeToCode.put(float.class.getCanonicalName(),     "F");
		typeToCode.put(double.class.getCanonicalName(),    "D");
		typeToCode.put(boolean.class.getCanonicalName(),   "Z");
		typeToCode.put(char.class.getCanonicalName(),      "C");
	}
	
	private final Map<String, Integer> codeToSize = new HashMap<String, Integer>();
	
	{
		codeToSize.put("J", 2);
		codeToSize.put("D", 2);
	}
	
	private final Map<String, Integer> codeToLoadOperation = new HashMap<String, Integer>();
	
	{
		codeToLoadOperation.put("B", ILOAD);
		codeToLoadOperation.put("S", ILOAD);
		codeToLoadOperation.put("I", ILOAD);
		codeToLoadOperation.put("J", LLOAD);
		codeToLoadOperation.put("F", FLOAD);
		codeToLoadOperation.put("D", DLOAD);
		codeToLoadOperation.put("Z", ILOAD);
		codeToLoadOperation.put("C", ILOAD);
	}
	
	private final Map<String, Integer> codeToReturnOperation = new HashMap<String, Integer>();
	
	{
		codeToReturnOperation.put("B", IRETURN);
		codeToReturnOperation.put("S", IRETURN);
		codeToReturnOperation.put("I", IRETURN);
		codeToReturnOperation.put("J", LRETURN);
		codeToReturnOperation.put("F", FRETURN);
		codeToReturnOperation.put("D", DRETURN);
		codeToReturnOperation.put("Z", IRETURN);
		codeToReturnOperation.put("C", IRETURN);
	}
	
	private String getCodeForType(String canonicalName) {
		if (canonicalName.endsWith("[]")) {
			return "[" + getCodeForType(canonicalName.substring(0, canonicalName.length() - 2));
		} else {
			String code = typeToCode.get(canonicalName);
			return code == null ? "L" + canonicalName.replace('.', '/') + ";" : code;
		}
	}

	private Integer getSizeForCode(String code) {
		Integer size = codeToSize.get(code);
		return size == null ? 1 : size;
	}

	private Integer getLoadOperationForCode(String code) {
		Integer operation = codeToLoadOperation.get(code);
		return operation == null ? ALOAD : operation;
	}

	private Integer getReturnOperationForCode(String code) {
		Integer operation = codeToReturnOperation.get(code);
		return operation == null ? ARETURN: operation;
	}

	public byte[] create(String canonicalClassName, Class<?> superclass, String[]... fields) throws Exception {
		ClassWriter cw = new ClassWriter(0);
		FieldVisitor fv;
		MethodVisitor mv;

		String typeCode = getCodeForType(canonicalClassName);
		String simpleClassName = canonicalClassName.substring(canonicalClassName.lastIndexOf('.') + 1);
		String canonicalClassNameWithSlashes = canonicalClassName.replace('.', '/');
		String canonicalSuperclassNameWithSlashes = superclass.getCanonicalName().replace('.', '/');
		cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, canonicalClassNameWithSlashes, null, canonicalSuperclassNameWithSlashes, new String[] { "java/io/Serializable" });
		cw.visitSource(simpleClassName + ".java", null);

		String constructorArgs = "(";
		for (String[] field : fields) {
			String propertyType = field[0];
			String propertyName = field[1];
			String propertyTypeCode = getCodeForType(propertyType);
			constructorArgs += propertyTypeCode;
			// create field
			fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, propertyName, propertyTypeCode, null, null);
			// a Collection<Integer>
			//fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "field09", "Ljava/util/Collection;", "Ljava/util/Collection<Ljava/lang/Integer;>;", null);
			// a Collection<Integer>[]
			//fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "field19", "[Ljava/util/Collection;", "[Ljava/util/Collection<Ljava/lang/Integer;>;", null);
			fv.visitEnd();
		}
		constructorArgs += ")V";

		{
			// create constructor
			int iloadCounter = 1;
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", constructorArgs, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, canonicalSuperclassNameWithSlashes, "<init>", "()V");
			for (String[] field : fields) {
				String propertyType = field[0];
				String propertyName = field[1];
				String propertyTypeCode = getCodeForType(propertyType);
				Label label = new Label();
				mv.visitLabel(label);
				mv.visitVarInsn(ALOAD, 0);
				int operation = getLoadOperationForCode(propertyTypeCode);
				mv.visitVarInsn(operation, iloadCounter);
				iloadCounter += getSizeForCode(propertyTypeCode);
				mv.visitFieldInsn(PUTFIELD, canonicalClassNameWithSlashes, propertyName, propertyTypeCode);
				
				// Object, Collection<Integer>
				//mv.visitVarInsn(ALOAD, 0);
				//mv.visitVarInsn(ALOAD, 12);
				//mv.visitFieldInsn(PUTFIELD, "org/dada/asm/Dummy", "field09", "Ljava/util/Collection;");
				
				
			}
			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitInsn(RETURN);
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLocalVariable("this", typeCode, null, l0, l3, 0);
			
			// initialise fields
			int counter = 1;
			for (String[] field : fields) {
				String propertyType = field[0];
				String propertyName = field[1];
				String propertyTypeCode = getCodeForType(propertyType);
				mv.visitLocalVariable(propertyName, propertyTypeCode, null, l0, l3, counter);
				counter += getSizeForCode(propertyTypeCode);
				
				//mv.visitLocalVariable("field08", "Ljava/lang/Object;", null, l0, l22, 11);
				//mv.visitLocalVariable("field09", "Ljava/util/Collection;", "Ljava/util/Collection<Ljava/lang/Integer;>;", l0, l22, 12);
				//mv.visitLocalVariable("field18", "[Ljava/lang/Object;", null, l0, l22, 21);
				//mv.visitLocalVariable("field19", "[Ljava/util/Collection;", null, l0, l22, 22);
			}
			mv.visitMaxs(3, counter);
			mv.visitEnd();
		}
		
		// create getters
		for (String[] field : fields) {
			String propertyType = field[0];
			String propertyName = field[1];
			String propertyTypeCode = getCodeForType(propertyType);
			String methodName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
			mv = cw.visitMethod(ACC_PUBLIC, methodName, "()" + propertyTypeCode, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, canonicalClassNameWithSlashes, propertyName, propertyTypeCode);
			mv.visitInsn(getReturnOperationForCode(propertyTypeCode));
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", typeCode, null, l0, l1, 0);
			mv.visitMaxs(getSizeForCode(propertyTypeCode), 1);
			mv.visitEnd();
		}

		cw.visitEnd();

		return cw.toByteArray();
	}
}
