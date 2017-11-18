package com.blade.ioc.reader;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 根据jar文件读取类
 *
 * @author <a href="mailto:biezhi.me@gmail.com" target="_blank">biezhi</a>
 * @since 1.0
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class JarReaderImpl extends AbstractClassReader implements ClassReader {

    private static final String JAR_FILE   = "jar:file:";
    private static final String WSJAR_FILE = "wsjar:file:";

    @Override
    public Set<ClassInfo> getClass(String packageName, boolean recursive) {
        return this.getClassByAnnotation(packageName, null, null, recursive);
    }

    @Override
    public Set<ClassInfo> getClass(String packageName, Class<?> parent, boolean recursive) {
        return this.getClassByAnnotation(packageName, parent, null, recursive);
    }

    @Override
    public Set<ClassInfo> getClassByAnnotation(String packageName, Class<? extends Annotation> annotation, boolean recursive) {
        return this.getClassByAnnotation(packageName, null, annotation, recursive);
    }

    @Override
    public Set<ClassInfo> getClassByAnnotation(String packageName, Class<?> parent, Class<? extends Annotation> annotation, boolean recursive) {
        Set<ClassInfo> classes = new HashSet<>();
        // 获取包的名字 并进行替换
        String packageDirName = packageName.replace('.', '/');
        // 定义一个枚举的集合 并进行循环来处理这个目录下的URL
        Enumeration<URL> dirs;
        try {
            dirs = this.getClass().getClassLoader().getResources(packageDirName);
            // 循环迭代下去
            while (dirs.hasMoreElements()) {
                // 获取下一个元素
                URL            url        = dirs.nextElement();
                Set<ClassInfo> subClasses = this.getClasses(url, packageDirName, packageName, parent, annotation, recursive, classes);
                if (subClasses.size() > 0) {
                    classes.addAll(subClasses);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return classes;
    }

    private Set<ClassInfo> getClasses(final URL url, final String packageDirName, String packageName, final Class<?> parent,
                                      final Class<? extends Annotation> annotation, final boolean recursive, Set<ClassInfo> classes) {
        try {
            if (url.toString().startsWith(JAR_FILE) || url.toString().startsWith(WSJAR_FILE)) {

                // 获取jar
                JarFile jarFile = ((JarURLConnection) url.openConnection()).getJarFile();

                // 从此jar包 得到一个枚举类
                Enumeration<JarEntry> eje = jarFile.entries();

                // 同样的进行循环迭代
                while (eje.hasMoreElements()) {
                    // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                    JarEntry entry = eje.nextElement();
                    String   name  = entry.getName();
                    // 如果是以/开头的
                    if (name.charAt(0) == '/') {
                        // 获取后面的字符串
                        name = name.substring(1);
                    }
                    // 如果前半部分和定义的包名相同
                    if (name.startsWith(packageDirName)) {
                        int idx = name.lastIndexOf('/');
                        // 如果以"/"结尾 是一个包
                        if (idx != -1) {
                            // 获取包名 把"/"替换成"."
                            packageName = name.substring(0, idx).replace('/', '.');
                        }
                        // 如果可以迭代下去 并且是一个包
                        if ((idx != -1) || recursive) {
                            // 如果是一个.class文件 而且不是目录
                            if (name.endsWith(".class") && !entry.isDirectory()) {
                                // 去掉后面的".class" 获取真正的类名
                                String className = name.substring(packageName.length() + 1, name.length() - 6);
                                // 添加到classes
                                Class<?> clazz = Class.forName(packageName + '.' + className);
                                if (null != parent && null != annotation) {
                                    if (null != clazz.getSuperclass() &&
                                            clazz.getSuperclass().equals(parent) && null != clazz.getAnnotation(annotation)) {
                                        classes.add(new ClassInfo(clazz));
                                    }
                                    continue;
                                }
                                if (null != parent) {
                                    if (null != clazz.getSuperclass() && clazz.getSuperclass().equals(parent)) {
                                        classes.add(new ClassInfo(clazz));
                                    }
                                    continue;
                                }
                                if (null != annotation) {
                                    if (null != clazz.getAnnotation(annotation)) {
                                        classes.add(new ClassInfo(clazz));
                                    }
                                    continue;
                                }
                                classes.add(new ClassInfo(clazz));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("The scan error when the user to define the view from a jar package file.", e);
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return classes;
    }
}
