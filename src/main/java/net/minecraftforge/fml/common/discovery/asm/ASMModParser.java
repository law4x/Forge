/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.discovery.asm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ReflectionAPI;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;

import org.apache.logging.log4j.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ASMModParser
{

    private Type asmType;
    private int classVersion;
    private Type asmSuperType;
    private LinkedList<ModAnnotation> annotations = Lists.newLinkedList();
    private Set<String> interfaces = Sets.newHashSet();
    private String baseModProperties;

    static enum AnnotationType
    {
        CLASS, FIELD, METHOD, SUBTYPE;
    }

    public ASMModParser(InputStream stream) throws IOException
    {
        try
        {
        	byte[] classBytes = readClass(stream, false);
        	byte[] decrytBytes = ReflectionAPI.decrytClass(MinecraftForge.class.getClassLoader(), classBytes);
        	ClassReader reader = new ClassReader(decrytBytes);
            reader.accept(new ModClassVisitor(this), 0);
        }
        catch (Exception ex)
        {
            FMLLog.log(Level.ERROR, ex, "Unable to read a class file correctly");
            throw new LoaderException(ex);
        }
    }
    

    private static byte[] readClass(final InputStream is, boolean close)
            throws IOException {
        if (is == null) {
            throw new IOException("Class not found");
        }
        try {
            byte[] b = new byte[is.available()];
            int len = 0;
            while (true) {
                int n = is.read(b, len, b.length - len);
                if (n == -1) {
                    if (len < b.length) {
                        byte[] c = new byte[len];
                        System.arraycopy(b, 0, c, 0, len);
                        b = c;
                    }
                    return b;
                }
                len += n;
                if (len == b.length) {
                    int last = is.read();
                    if (last < 0) {
                        return b;
                    }
                    byte[] c = new byte[b.length + 1000];
                    System.arraycopy(b, 0, c, 0, len);
                    c[len++] = (byte) last;
                    b = c;
                }
            }
        } finally {
            if (close) {
                is.close();
            }
        }
    }
    

    public void beginNewTypeName(String typeQName, int classVersion, String superClassQName, String[] interfaces)
    {
        this.asmType = Type.getObjectType(typeQName);
        this.classVersion = classVersion;
        this.asmSuperType = !Strings.isNullOrEmpty(superClassQName) ? Type.getObjectType(superClassQName) : null;
        for (String intf : interfaces)
            this.interfaces.add(intf);
    }

    public void startClassAnnotation(String annotationName)
    {
        ModAnnotation ann = new ModAnnotation(AnnotationType.CLASS, Type.getType(annotationName), this.asmType.getClassName());
        annotations.addFirst(ann);
    }

    public void addAnnotationProperty(String key, Object value)
    {
        annotations.getFirst().addProperty(key, value);
    }

    public void startFieldAnnotation(String fieldName, String annotationName)
    {
        ModAnnotation ann = new ModAnnotation(AnnotationType.FIELD, Type.getType(annotationName), fieldName);
        annotations.addFirst(ann);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper("ASMAnnotationDiscoverer")
                .add("className", asmType.getClassName())
                .add("classVersion", classVersion)
                .add("superName", asmSuperType.getClassName())
                .add("annotations", annotations)
                .add("isBaseMod", isBaseMod(Collections.<String>emptyList()))
                .add("baseModProperties", baseModProperties)
                .toString();
    }

    public Type getASMType()
    {
        return asmType;
    }

    public int getClassVersion()
    {
        return classVersion;
    }

    public Type getASMSuperType()
    {
        return asmSuperType;
    }

    public LinkedList<ModAnnotation> getAnnotations()
    {
        return annotations;
    }

    public void validate()
    {
//        if (classVersion > 50.0)
//        {
//
//            throw new LoaderException(new RuntimeException("Mod compiled for Java 7 detected"));
//        }
    }

    public boolean isBaseMod(List<String> rememberedTypes)
    {
        return getASMSuperType().equals(Type.getType("LBaseMod;")) || getASMSuperType().equals(Type.getType("Lnet/minecraft/src/BaseMod;"))|| rememberedTypes.contains(getASMSuperType().getClassName());
    }

    public void setBaseModProperties(String foundProperties)
    {
        this.baseModProperties = foundProperties;
    }

    public String getBaseModProperties()
    {
        return this.baseModProperties;
    }

    public void sendToTable(ASMDataTable table, ModCandidate candidate)
    {
        for (ModAnnotation ma : annotations)
        {
            table.addASMData(candidate, ma.asmType.getClassName(), this.asmType.getClassName(), ma.member, ma.values);
        }

        for (String intf : interfaces)
        {
            table.addASMData(candidate, intf, this.asmType.getInternalName(), null, null);
        }
    }

    public void addAnnotationArray(String name)
    {
        annotations.getFirst().addArray(name);
    }

    public void addAnnotationEnumProperty(String name, String desc, String value)
    {
        annotations.getFirst().addEnumProperty(name, desc, value);

    }

    public void endArray()
    {
        annotations.getFirst().endArray();

    }

    public void addSubAnnotation(String name, String desc)
    {
        ModAnnotation ma = annotations.getFirst();
        annotations.addFirst(ma.addChildAnnotation(name, desc));
    }

    public void endSubAnnotation()
    {
        // take the child and stick it at the end
        ModAnnotation child = annotations.removeFirst();
        annotations.addLast(child);
    }

    public void startMethodAnnotation(String methodName, String methodDescriptor, String annotationName)
    {
        ModAnnotation ann = new ModAnnotation(AnnotationType.METHOD, Type.getType(annotationName), methodName+methodDescriptor);
        annotations.addFirst(ann);
    }
}
